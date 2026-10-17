/*
 * Copyright (c) 2024-present ypbin-starter authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.ypbin.starter.tools.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

/**
 * {@link DistributedLock#autoRenew()} 自动续期行为测试（真实 AOP 代理织入 + 真实内存锁）。
 *
 * <p><b>为什么不能只断言「renew 被调用过」</b>：本类用「可注入时钟 + 真实 {@link InMemoryLockService}」
 * 断言存储里的**过期时刻真的被前移**，用「手动调度器」断言注册/注销次数与间隔，用 Logback
 * {@link ListAppender} 断言续期失败确实告警（异常路径还断言带完整堆栈）。这四类证据合起来才证明
 * 语义成立，而不是「方法被调用」这种自我循环。</p>
 *
 * <p>五个必测点：① TTL 被延长；② 续期失败后停止并告警；③ 方法结束/抛异常后注销调度；
 * ④ 未开启 autoRenew 时零调度行为；⑤ 重入场景只注册一次。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
class DistributedLockAspectRenewalTest {

    /** 注解 {@code key = "biz"} 解析后的真实锁键。 */
    private static final String LOCK_KEY = "ypbin:lock:biz";

    /** 固定起始时刻（可注入时钟，断言精确到毫秒，不真等）。 */
    private static final long START_MILLIS = 1_700_000_000_000L;

    private MutableClock clock;

    private InMemoryLockService lock;

    private ManualLockRenewalScheduler scheduler;

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START_MILLIS);
        lock = new InMemoryLockService(clock);
        scheduler = new ManualLockRenewalScheduler();
        appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(DistributedLockAspect.class)).addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(DistributedLockAspect.class)).detachAppender(appender);
    }

    // ------------------------------------------------------------------ ① TTL 被延长

    /** 在方法执行中途手动推进时钟并触发一次续期，记录续期前后的过期时刻。 */
    public static class RenewingService {

        InMemoryLockService lock;

        MutableClock clock;

        ManualLockRenewalScheduler scheduler;

        Long expireAtBefore;

        Long expireAtAfter;

        @DistributedLock(key = "biz", ttl = 30, autoRenew = true)
        public String run() {
            expireAtBefore = lock.expireAtMillis(LOCK_KEY);
            // 模拟「调度到点」：时间前移 10s 后续期，过期时刻应随之整体前移
            clock.advance(10_000);
            scheduler.fireAll();
            expireAtAfter = lock.expireAtMillis(LOCK_KEY);
            return "ok";
        }
    }

    @Test
    @DisplayName("① 续期把存储中的过期时刻前移（不是「方法被调用」而是真实 TTL 变化）")
    void renewalExtendsExpiryInStore() {
        RenewingService target = new RenewingService();
        target.lock = lock;
        target.clock = clock;
        target.scheduler = scheduler;

        assertThat(proxy(target, RenewingService.class).run()).isEqualTo("ok");

        assertThat(target.expireAtBefore)
            .as("加锁时的过期时刻 = 起始时刻 + ttl(30s)")
            .isEqualTo(START_MILLIS + 30_000);
        assertThat(target.expireAtAfter)
            .as("续期后的过期时刻 = (起始时刻 + 10s) + ttl(30s)，确实被推后")
            .isEqualTo(START_MILLIS + 10_000 + 30_000);
        assertThat(target.expireAtAfter).isGreaterThan(target.expireAtBefore);
        assertThat(scheduler.intervals())
            .as("默认续期间隔 = ttl/3 = 10s")
            .containsExactly(Duration.ofSeconds(10));
    }

    // ------------------------------------------------------------------ ④ 未开启 autoRenew：零调度

    public static class PlainService {

        @DistributedLock(key = "biz")
        public String run() {
            return "ok";
        }
    }

    @Test
    @DisplayName("④ 未开启 autoRenew：不注册任何续期、不注销、无任何调度行为")
    void noSchedulingWhenAutoRenewDisabled() {
        assertThat(proxy(new PlainService(), PlainService.class).run()).isEqualTo("ok");

        assertThat(scheduler.scheduleCount()).isZero();
        assertThat(scheduler.cancelCount()).isZero();
        assertThat(scheduler.intervals()).isEmpty();
    }

    // ------------------------------------------------------------------ ③ 方法结束/异常后注销

    public static class RenewingThrowingService {

        @DistributedLock(key = "biz", ttl = 30, autoRenew = true)
        public String run() {
            throw new IllegalStateException("boom");
        }
    }

    @Test
    @DisplayName("③ 方法正常结束：注销续期调度并释放锁")
    void renewalDeregisteredAfterNormalExit() {
        RenewingService target = new RenewingService();
        target.lock = lock;
        target.clock = clock;
        target.scheduler = scheduler;

        assertThat(proxy(target, RenewingService.class).run()).isEqualTo("ok");

        assertThat(scheduler.scheduleCount()).isEqualTo(1);
        assertThat(scheduler.cancelCount()).as("退出时必须注销，否则续期任务泄漏").isEqualTo(1);
        assertThat(lock.mapSize()).as("锁已释放").isZero();
    }

    @Test
    @DisplayName("③ 方法抛异常：同样注销续期调度并释放锁（异常照常上抛）")
    void renewalDeregisteredAfterException() {
        assertThatThrownBy(() -> proxy(new RenewingThrowingService(), RenewingThrowingService.class).run())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("boom");

        assertThat(scheduler.scheduleCount()).isEqualTo(1);
        assertThat(scheduler.cancelCount()).isEqualTo(1);
        assertThat(lock.mapSize()).isZero();
    }

    // ------------------------------------------------------------------ ② 续期失败：告警 + 停止

    /** 真实内存锁语义 + 续期恒失败（模拟「锁已被他人接管」）。 */
    static final class RenewFailingLockService implements LockService {

        private final InMemoryLockService delegate = new InMemoryLockService();

        final AtomicInteger renewCount = new AtomicInteger();

        @Override
        public boolean tryLock(String key, String owner, Duration ttl) {
            return delegate.tryLock(key, owner, ttl);
        }

        @Override
        public boolean renew(String key, String owner, Duration ttl) {
            renewCount.incrementAndGet();
            return false;
        }

        @Override
        public boolean unlock(String key, String owner) {
            return delegate.unlock(key, owner);
        }
    }

    /** 真实内存锁语义 + 续期抛异常（模拟「存储不可用」）。 */
    static final class RenewThrowingLockService implements LockService {

        private final InMemoryLockService delegate = new InMemoryLockService();

        @Override
        public boolean tryLock(String key, String owner, Duration ttl) {
            return delegate.tryLock(key, owner, ttl);
        }

        @Override
        public boolean renew(String key, String owner, Duration ttl) {
            throw new IllegalStateException("redis unavailable");
        }

        @Override
        public boolean unlock(String key, String owner) {
            return delegate.unlock(key, owner);
        }
    }

    public static class DoubleFireService {

        ManualLockRenewalScheduler scheduler;

        @DistributedLock(key = "biz", ttl = 30, autoRenew = true)
        public String run() {
            scheduler.fireAll();
            // 失败后任务应已被注销：再次触发不得再调用 renew
            scheduler.fireAll();
            return "ok";
        }
    }

    @Test
    @DisplayName("② 续期返回 false（失去锁）：记 WARN 并停止续期，不再重复续期")
    void renewalFailureWarnsAndStops() {
        RenewFailingLockService failing = new RenewFailingLockService();
        DoubleFireService target = new DoubleFireService();
        target.scheduler = scheduler;

        assertThat(proxy(target, new DistributedLockAspect(failing, scheduler), DoubleFireService.class).run())
            .isEqualTo("ok");

        assertThat(failing.renewCount)
            .as("第一次续期失败后必须停止续期，第二次触发不得再调用 renew")
            .hasValue(1);
        assertThat(scheduler.cancelCount()).as("失败即注销调度").isEqualTo(1);

        List<ILoggingEvent> warns = warns();
        assertThat(warns).hasSize(1);
        assertThat(warns.get(0).getFormattedMessage())
            .contains("续期失败")
            .contains(LOCK_KEY);
    }

    @Test
    @DisplayName("② 续期抛异常：记 WARN（带完整堆栈）并停止续期")
    void renewalExceptionWarnsWithStackAndStops() {
        RenewThrowingLockService failing = new RenewThrowingLockService();
        DoubleFireService target = new DoubleFireService();
        target.scheduler = scheduler;

        assertThat(proxy(target, new DistributedLockAspect(failing, scheduler), DoubleFireService.class).run())
            .isEqualTo("ok");

        assertThat(scheduler.cancelCount()).isEqualTo(1);
        List<ILoggingEvent> warns = warns();
        assertThat(warns).hasSize(1);
        assertThat(warns.get(0).getFormattedMessage()).contains("续期异常").contains(LOCK_KEY);
        assertThat(warns.get(0).getThrowableProxy())
            .as("存储异常必须带完整堆栈（规范要求 log.warn(..., ex)），不得只记一行文案")
            .isNotNull();
        assertThat(warns.get(0).getThrowableProxy().getClassName())
            .isEqualTo(IllegalStateException.class.getName());
    }

    // ------------------------------------------------------------------ ⑤ 重入只注册一次

    public static class InnerRenewService {

        @DistributedLock(key = "biz", ttl = 30, autoRenew = true)
        public String inner() {
            return "inner";
        }
    }

    public static class OuterRenewService {

        InnerRenewService inner;

        @DistributedLock(key = "biz", ttl = 30, autoRenew = true)
        public String outer() {
            return "outer->" + inner.inner();
        }
    }

    @Test
    @DisplayName("⑤ 同线程同 key 重入：只有最外层注册一次续期，退出时只注销一次")
    void reentrantNestingRegistersRenewalOnce() {
        DistributedLockAspect aspect = new DistributedLockAspect(lock, scheduler);

        InnerRenewService innerTarget = new InnerRenewService();
        InnerRenewService inner = proxy(innerTarget, aspect, InnerRenewService.class);

        OuterRenewService outerTarget = new OuterRenewService();
        outerTarget.inner = inner;
        OuterRenewService outer = proxy(outerTarget, aspect, OuterRenewService.class);

        assertThat(outer.outer()).isEqualTo("outer->inner");
        assertThat(scheduler.scheduleCount()).as("内层重入不得重复注册续期").isEqualTo(1);
        assertThat(scheduler.cancelCount()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ 配置校验与间隔解析

    public static class ExplicitIntervalService {

        @DistributedLock(key = "biz", ttl = 30, autoRenew = true, renewIntervalSeconds = 5)
        public String run() {
            return "ok";
        }
    }

    public static class ShortTtlService {

        @DistributedLock(key = "biz", ttl = 2, autoRenew = true)
        public String run() {
            return "ok";
        }
    }

    public static class TtlTooSmallForRenewService {

        @DistributedLock(key = "biz", ttl = 1, autoRenew = true)
        public String run() {
            return "ok";
        }
    }

    public static class BadIntervalService {

        @DistributedLock(key = "biz", ttl = 10, autoRenew = true, renewIntervalSeconds = 10)
        public String run() {
            return "ok";
        }
    }

    public static class NegativeIntervalService {

        @DistributedLock(key = "biz", ttl = 10, autoRenew = true, renewIntervalSeconds = -1)
        public String run() {
            return "ok";
        }
    }

    public static class ZeroTtlService {

        @DistributedLock(key = "biz", ttl = 0, autoRenew = true)
        public String run() {
            return "ok";
        }
    }

    @Test
    @DisplayName("显式续期间隔生效（renewIntervalSeconds > 0 时不再按 ttl/3）")
    void explicitIntervalIsUsed() {
        assertThat(proxy(new ExplicitIntervalService(), ExplicitIntervalService.class).run()).isEqualTo("ok");
        assertThat(scheduler.intervals()).containsExactly(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("ttl 过小：默认间隔取下限 1s，仍严格小于 ttl")
    void tinyTtlFallsBackToMinimumInterval() {
        assertThat(proxy(new ShortTtlService(), ShortTtlService.class).run()).isEqualTo("ok");
        assertThat(scheduler.intervals()).containsExactly(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("配置错误 fail-fast：间隔 >= ttl / ttl<=0 / 间隔为负，都在抢锁前抛出")
    void invalidRenewConfigFailsFastBeforeAcquiring() {
        assertThatThrownBy(() -> proxy(new BadIntervalService(), BadIntervalService.class).run())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("必须小于");
        assertThatThrownBy(() -> proxy(new TtlTooSmallForRenewService(), TtlTooSmallForRenewService.class).run())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("必须小于");
        assertThatThrownBy(() -> proxy(new ZeroTtlService(), ZeroTtlService.class).run())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ttl 必须为正数");
        assertThatThrownBy(() -> proxy(new NegativeIntervalService(), NegativeIntervalService.class).run())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("不能为负数");

        assertThat(lock.mapSize())
            .as("配置错误必须在抢锁前暴露，不得先占锁再抛（否则还会留下一次无谓的加锁/释放）")
            .isZero();
        assertThat(scheduler.scheduleCount()).isZero();
    }

    // ------------------------------------------------------------------ 注册竞态：句柄返回前已失败

    public static class SimpleRenewService {

        @DistributedLock(key = "biz", ttl = 30, autoRenew = true)
        public String run() {
            return "ok";
        }
    }

    /**
     * 在 {@link #schedule} 内部同步触发一次续期（模拟「调度器在返回句柄之前就已经跑到点」的竞态窗口）。
     *
     * <p>真实线程调度下这个窗口极窄但存在；用同步替身把它变成确定性事实。</p>
     */
    static final class SynchronousFiringScheduler implements LockRenewalScheduler {

        private final AtomicInteger cancelCount = new AtomicInteger();

        @Override
        public Registration schedule(Runnable renewalTask, Duration interval) {
            AtomicBoolean cancelled = new AtomicBoolean();
            Registration registration = () -> {
                if (cancelled.compareAndSet(false, true)) {
                    cancelCount.incrementAndGet();
                }
            };
            // 竞态窗口：句柄尚未返回给切面，首次续期已经执行（此处必然失败）
            renewalTask.run();
            return registration;
        }

        int cancelCount() {
            return cancelCount.get();
        }
    }

    @Test
    @DisplayName("注册返回前首次续期即失败：句柄仍必须被注销（否则重复任务永久残留到应用关闭）")
    void registrationCancelledWhenFirstRenewalFailsBeforeHandleIsAssigned() {
        RenewFailingLockService failing = new RenewFailingLockService();
        SynchronousFiringScheduler sync = new SynchronousFiringScheduler();

        assertThat(proxy(new SimpleRenewService(), new DistributedLockAspect(failing, sync), SimpleRenewService.class)
            .run()).isEqualTo("ok");

        assertThat(failing.renewCount).as("首次续期失败后已停止续期").hasValue(1);
        assertThat(sync.cancelCount())
            .as("即使失败发生在句柄返回之前，注册也必须被注销（stopRenewal 当时还看不到它）")
            .isEqualTo(1);
        assertThat(lock.mapSize()).as("锁已正常释放").isZero();
    }

    // ------------------------------------------------------------------ 辅助

    private <T> T proxy(T target, Class<T> type) {
        return proxy(target, new DistributedLockAspect(lock, scheduler), type);
    }

    private <T> T proxy(T target, DistributedLockAspect aspect, Class<T> type) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        return type.cast(factory.getProxy());
    }

    private List<ILoggingEvent> warns() {
        return appender.list.stream().filter(event -> event.getLevel() == Level.WARN).toList();
    }
}

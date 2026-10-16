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

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

/**
 * {@link DistributedLockAspect} 本地重入（同线程同 key）行为测试。
 *
 * <p>用真实 {@link InMemoryLockService} 作为锁存储：它按 owner 判归属，因此能真实复现
 * 「每次调用都用新 UUID 当 owner ⇒ 内层拿不到自己刚加的锁」这一缺陷，而不是用一个永远返回
 * true 的假锁把问题盖掉。外面再包一层计数代理，用来断言「只加锁一次、只有最外层释放一次」。</p>
 *
 * <p>两个服务对象共用**同一个切面实例**，对应生产环境里单例切面 Bean 被所有被代理 Bean 共享的
 * 真实形态（若各用各的切面实例，ThreadLocal 状态本就互不可见，测不出重入）。</p>
 *
 * <p>注意「目标对象 vs 代理对象」：Spring AOP 的 CGLIB 代理把方法调用转发给目标实例，方法体
 * 修改的是**目标对象**的字段；代理子类上另有一份同名字段且永不被写。故本测试的计数器与闩锁
 * 一律保留目标引用（{@code innerTarget}/{@code outerTarget}）来读写，调用走代理引用。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
class DistributedLockAspectReentrancyTest {

    /** 真实内存锁 + 加锁/释放计数：保留 owner 归属语义，同时可断言调用次数。 */
    static class CountingLockService implements LockService {

        final InMemoryLockService delegate = new InMemoryLockService();

        final AtomicInteger tryLockCount = new AtomicInteger();

        final AtomicInteger unlockCount = new AtomicInteger();

        @Override
        public boolean tryLock(String key, String owner, Duration ttl) {
            tryLockCount.incrementAndGet();
            return delegate.tryLock(key, owner, ttl);
        }

        @Override
        public boolean unlock(String key, String owner) {
            unlockCount.incrementAndGet();
            return delegate.unlock(key, owner);
        }
    }

    /** 最内层服务：锁键 "biz"，以及一个不同 key / SpEL key 的版本。 */
    public static class InnerService {

        final AtomicInteger executed = new AtomicInteger();

        @DistributedLock(key = "biz")
        public String inner() {
            executed.incrementAndGet();
            return "inner";
        }

        @DistributedLock(key = "other")
        public String innerOtherKey() {
            executed.incrementAndGet();
            return "inner-other";
        }

        @DistributedLock(key = "biz")
        public String innerThrows() {
            executed.incrementAndGet();
            throw new IllegalStateException("inner-boom");
        }

        /** EXCEPTION 策略：修复前内层拿不到锁会抛 {@link LockAcquireException}，而不是「死锁」。 */
        @DistributedLock(key = "biz", failStrategy = DistributedLock.FailStrategy.EXCEPTION)
        public String innerOrThrow() {
            executed.incrementAndGet();
            return "inner";
        }

        @DistributedLock(key = "#id")
        public String spelInner(Long id) {
            executed.incrementAndGet();
            return "inner-" + id;
        }
    }

    /** 中间层：验证三层嵌套下深度累加、释放仍只在最外层发生。 */
    public static class MidService {

        InnerService inner;

        final AtomicInteger executed = new AtomicInteger();

        @DistributedLock(key = "biz")
        public String mid() {
            executed.incrementAndGet();
            return "mid->" + inner.inner();
        }
    }

    /** 最外层服务：嵌套调用内层（跨 Bean 调用才会经过代理，注解才生效）。 */
    public static class OuterService {

        InnerService inner;

        MidService mid;

        final AtomicInteger executed = new AtomicInteger();

        CountDownLatch entered;

        CountDownLatch gate;

        @DistributedLock(key = "biz")
        public String outer() {
            executed.incrementAndGet();
            return "outer->" + inner.inner();
        }

        @DistributedLock(key = "biz")
        public String outerViaMid() {
            executed.incrementAndGet();
            return "outer->" + mid.mid();
        }

        @DistributedLock(key = "biz")
        public String outerOtherKey() {
            executed.incrementAndGet();
            return "outer->" + inner.innerOtherKey();
        }

        @DistributedLock(key = "biz")
        public String outerThrows() {
            executed.incrementAndGet();
            return "outer->" + inner.innerThrows();
        }

        @DistributedLock(key = "biz")
        public String outerOrThrow() {
            executed.incrementAndGet();
            return "outer->" + inner.innerOrThrow();
        }

        @DistributedLock(key = "#id")
        public String spelOuter(Long id) {
            executed.incrementAndGet();
            return "outer->" + inner.spelInner(id);
        }

        /** 持锁阻塞，供「ThreadLocal 不跨线程」用例让另一线程在持锁期间尝试同 key。 */
        @DistributedLock(key = "biz")
        public String outerBlocking() throws InterruptedException {
            executed.incrementAndGet();
            entered.countDown();
            gate.await(5, TimeUnit.SECONDS);
            return "blocked";
        }
    }

    private CountingLockService lock;

    private InnerService innerTarget;

    private InnerService inner;

    private OuterService outerTarget;

    private OuterService outer;

    @BeforeEach
    void setUp() {
        lock = new CountingLockService();
        DistributedLockAspect aspect = new DistributedLockAspect(lock);

        innerTarget = new InnerService();
        inner = proxy(innerTarget, aspect, InnerService.class);

        MidService midTarget = new MidService();
        midTarget.inner = inner;
        MidService mid = proxy(midTarget, aspect, MidService.class);

        outerTarget = new OuterService();
        outerTarget.inner = inner;
        outerTarget.mid = mid;
        outerTarget.entered = new CountDownLatch(1);
        outerTarget.gate = new CountDownLatch(1);
        outer = proxy(outerTarget, aspect, OuterService.class);
    }

    private <T> T proxy(T target, DistributedLockAspect aspect, Class<T> type) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        return type.cast(factory.getProxy());
    }

    @Test
    @DisplayName("同线程同 key 嵌套：内层直接执行（不再被跳过），且只加锁/释放一次")
    void nestedSameKeyIsReentrant() {
        assertThat(outer.outer())
            .as("同线程同 key 的重入必须真正执行内层方法，而不是拿到 null")
            .isEqualTo("outer->inner");
        assertThat(innerTarget.executed).hasValue(1);
        assertThat(lock.tryLockCount)
            .as("重入不重复向锁存储要锁（owner 不同，内层必然失败）")
            .hasValue(1);
        assertThat(lock.unlockCount)
            .as("只有最外层退出时才释放")
            .hasValue(1);
    }

    @Test
    @DisplayName("三层嵌套同 key：只加锁一次，最外层退出才释放")
    void threeLevelNestingLocksOnce() {
        assertThat(outer.outerViaMid()).isEqualTo("outer->mid->inner");
        assertThat(lock.tryLockCount).hasValue(1);
        assertThat(lock.unlockCount).hasValue(1);
    }

    @Test
    @DisplayName("不同 key 不视为重入：各自加锁、各自释放")
    void differentKeysAreNotReentrant() {
        assertThat(outer.outerOtherKey()).isEqualTo("outer->inner-other");
        assertThat(lock.tryLockCount).hasValue(2);
        assertThat(lock.unlockCount).hasValue(2);
    }

    @Test
    @DisplayName("SpEL 解析后的 key 参与重入判定：同参重入、异参各自加锁")
    void reentrancyUsesResolvedSpelKey() {
        assertThat(outer.spelOuter(7L)).isEqualTo("outer->inner-7");
        assertThat(lock.tryLockCount).hasValue(1);
        assertThat(lock.unlockCount).hasValue(1);

        assertThat(outer.spelOuter(8L)).isEqualTo("outer->inner-8");
        assertThat(lock.tryLockCount).hasValue(2);
        assertThat(lock.unlockCount).hasValue(2);
    }

    @Test
    @DisplayName("嵌套内层抛异常：异常照常上抛，最外层仍释放，ThreadLocal 已清理可再次加锁")
    void nestedExceptionStillReleasesAndCleansUp() {
        assertThatThrownBy(() -> outer.outerThrows())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("inner-boom");
        assertThat(lock.unlockCount).as("异常路径也必须释放").hasValue(1);

        // ThreadLocal 未清理时，第二次调用会被误判为「重入」而不再调用 tryLock
        assertThat(outer.outer()).isEqualTo("outer->inner");
        assertThat(lock.tryLockCount).hasValue(2);
        assertThat(lock.unlockCount).hasValue(2);
    }

    @Test
    @DisplayName("最外层退出后清理 ThreadLocal：后续调用重新向锁存储加锁")
    void threadLocalCleanedUpAfterOutermostExit() {
        assertThat(outer.outer()).isEqualTo("outer->inner");
        assertThat(lock.tryLockCount).hasValue(1);

        assertThat(outer.outer()).isEqualTo("outer->inner");
        assertThat(lock.tryLockCount)
            .as("前一次调用残留的状态会让第二次被误判为重入")
            .hasValue(2);
        assertThat(lock.unlockCount).hasValue(2);
    }

    @Test
    @DisplayName("EXCEPTION 策略下的同 key 嵌套同样重入：不抛 LockAcquireException")
    void nestedSameKeyIsReentrantUnderExceptionStrategy() {
        assertThat(outer.outerOrThrow()).isEqualTo("outer->inner");
        assertThat(lock.tryLockCount).hasValue(1);
        assertThat(lock.unlockCount).hasValue(1);
    }

    @Test
    @DisplayName("ThreadLocal 不跨线程：持锁线程之外的同 key 调用仍按「抢不到锁」处理")
    void reentrancyIsPerThread() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<String> holding = pool.submit(() -> outer.outerBlocking());
            assertThat(outerTarget.entered.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(outer.outer())
                .as("另一个线程不得因本线程的重入状态而跳过加锁")
                .isNull();
            assertThat(lock.tryLockCount)
                .as("持锁期间另一线程的同 key 调用必须仍向锁存储尝试（并失败）")
                .hasValue(2);

            outerTarget.gate.countDown();
            assertThat(holding.get(5, TimeUnit.SECONDS)).isEqualTo("blocked");
            assertThat(lock.unlockCount).hasValue(1);
        } finally {
            outerTarget.gate.countDown();
            pool.shutdownNow();
        }
    }
}

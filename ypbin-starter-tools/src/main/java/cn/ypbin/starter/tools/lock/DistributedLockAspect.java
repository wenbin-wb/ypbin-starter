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

import cn.ypbin.starter.tools.lock.LockRenewalScheduler.Registration;
import cn.ypbin.starter.tools.support.SpelKeyResolver;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 分布式锁切面。
 *
 * <p>拦截 {@link DistributedLock} 方法：进入前尝试加锁（可配等待/重试），成功则执行并在结束后释放，
 * 失败按策略跳过（返回 null）或抛出 {@link LockAcquireException}。持有者用唯一 UUID，释放时校验，
 * 保证只释放自己持有的锁。</p>
 *
 * <p><strong>同线程同 key 重入</strong>：每次加锁的 owner 都是新 UUID，因此同一线程嵌套调用同一
 * 锁键时，内层若再次向 {@link LockService} 要锁必然失败（owner 不匹配），会被当作「锁被别的实例
 * 持有」而跳过（默认返回 null）或按 {@link DistributedLock.FailStrategy#EXCEPTION} 抛异常——这正是
 * 重构、模板方法等「外层加锁、内层复用同一临界区」的正常写法会踩到的坑。故切面在本地记录
 * 「本线程已按哪个 key 拿到锁、嵌套了多少层」：内层不再要锁，直接进入临界区；只有最外层退出时才
 * 真正 {@code unlock}，并清理 {@link ThreadLocal}（线程池复用下不留残余状态）。</p>
 *
 * <p>重入判定用的是 {@link #buildKey} 解析后的键（含 SpEL），与真实加锁键完全一致；且按**切面实例**
 * 隔离——切面是单例 Bean，等价于「同一把逻辑锁」才判重入。</p>
 *
 * <p><strong>自动续期（{@link DistributedLock#autoRenew()}）</strong>：开启后，只有**最外层**持锁会向
 * {@link LockRenewalScheduler} 注册一次续期（内层重入直接进入临界区，不重复注册）；续期任务在后台按
 * {@link DistributedLock#renewIntervalSeconds()} 周期性调用 {@link LockService#renew}，由存储层原子地
 * 保证「仍是自己的锁才延长 TTL」。方法正常返回或抛异常退出时，{@code finally} 先停止续期并注销调度、
 * 再释放锁。续期失败（锁已被接管）或续期异常（存储不可用）一律记 WARN 并**立即停止续期**，绝不静默
 * 假装仍持锁。默认关闭时切面完全不注册任何调度，行为与既有版本一致。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
@Aspect
public class DistributedLockAspect {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockAspect.class);

    /** 自动续期间隔下限（秒）：再小也只会徒增存储往返，锁本身不可能在毫秒级被安全续期。 */
    private static final int MIN_RENEW_INTERVAL_SECONDS = 1;

    /** 未显式配置续期间隔时的分母：{@code ttl/3} 允许连续两次续期失败才真正丢锁。 */
    private static final int DEFAULT_RENEW_INTERVAL_DIVISOR = 3;

    private final LockService lockService;

    /** 自动续期调度器（仅 {@link DistributedLock#autoRenew()} 为 true 时被使用）。 */
    private final LockRenewalScheduler renewalScheduler;

    /**
     * 本线程当前持有的锁（解析后的 key → owner + 重入深度）。
     *
     * <p>按切面实例隔离（等价于按 {@link LockService} 隔离）：只有同一把逻辑锁才判重入，避免两个
     * 不同锁服务上的同名 key 被误判。用普通 {@link ThreadLocal} 而非 {@code withInitial}：绝大多数
     * 调用并非重入，不该为每次加锁都分配一个 Map（惰性创建见 {@link #heldLocks()}）。</p>
     */
    private final ThreadLocal<Map<String, ReentrantState>> localLocks = new ThreadLocal<>();

    /**
     * @param lockService      锁服务
     * @param renewalScheduler 自动续期调度器（{@code autoRenew=false} 时不会被使用，但必须注入，
     *                         以免「忘记装配」表现为静默不续期）
     */
    public DistributedLockAspect(LockService lockService, LockRenewalScheduler renewalScheduler) {
        this.lockService = lockService;
        this.renewalScheduler = renewalScheduler;
    }

    @Around("@annotation(distributedLock)")
    @Nullable
    public Object around(ProceedingJoinPoint point, DistributedLock distributedLock) throws Throwable {
        String key = buildKey(point, distributedLock);

        // 同线程同 key 的重入：owner 不同，再向锁存储要一次必然失败，直接进入临界区
        ReentrantState reentrant = currentState(key);
        if (reentrant != null) {
            reentrant.depth++;
            try {
                return point.proceed();
            } finally {
                exitGuarded(key, reentrant, distributedLock);
            }
        }

        // fail-fast：续期配置错误（ttl<=0、显式间隔>=ttl）必须在抢锁前暴露，否则「抢到锁才发现配错」
        Duration renewInterval = resolveRenewInterval(distributedLock);

        String owner = UUID.randomUUID().toString();
        Duration ttl = Duration.ofSeconds(distributedLock.ttl());

        boolean locked = acquire(key, owner, ttl, distributedLock);
        if (!locked) {
            if (distributedLock.failStrategy() == DistributedLock.FailStrategy.EXCEPTION) {
                throw new LockAcquireException(distributedLock.message());
            }
            log.debug("[ypbin-starter] 未获取到分布式锁，跳过执行：key={}", key);
            return null;
        }

        ReentrantState state = new ReentrantState(owner);
        state.depth = 1;
        heldLocks().put(key, state);
        try {
            startRenewal(key, state, distributedLock, renewInterval);
            return point.proceed();
        } finally {
            exitGuarded(key, state, distributedLock);
        }
    }

    /**
     * 解析并校验自动续期间隔；未开启 {@code autoRenew} 时返回 {@code null}（不注册任何调度）。
     *
     * @param lock 注解
     * @return 续期间隔；未开启续期时为 {@code null}
     * @throws IllegalStateException ttl 非正、显式间隔为负、或间隔不小于 ttl（锁会在首次续期前过期）
     */
    @Nullable
    private Duration resolveRenewInterval(DistributedLock lock) {
        if (!lock.autoRenew()) {
            return null;
        }
        long ttlSeconds = lock.ttl();
        if (ttlSeconds <= 0) {
            throw new IllegalStateException("@DistributedLock.ttl 必须为正数才能自动续期：ttl=" + ttlSeconds);
        }
        int configured = lock.renewIntervalSeconds();
        if (configured < 0) {
            throw new IllegalStateException("@DistributedLock.renewIntervalSeconds 不能为负数："
                + "renewIntervalSeconds=" + configured);
        }
        long intervalSeconds = configured > 0
            ? configured
            : Math.max(MIN_RENEW_INTERVAL_SECONDS, ttlSeconds / DEFAULT_RENEW_INTERVAL_DIVISOR);
        if (intervalSeconds >= ttlSeconds) {
            throw new IllegalStateException("@DistributedLock 自动续期间隔(" + intervalSeconds
                + "s) 必须小于 ttl(" + ttlSeconds + "s)：否则锁会在第一次续期之前过期，多副本下会并发执行");
        }
        return Duration.ofSeconds(intervalSeconds);
    }

    /**
     * 注册最外层持锁的自动续期（仅在开启 {@code autoRenew} 时）。
     *
     * @param key      解析后的锁键
     * @param state    本次持锁状态
     * @param lock     注解
     * @param interval 续期间隔；{@code null} 表示未开启续期
     */
    private void startRenewal(String key, ReentrantState state, DistributedLock lock, @Nullable Duration interval) {
        if (interval == null) {
            return;
        }
        Registration registration = renewalScheduler.schedule(() -> renewGuarded(key, state, lock), interval);
        state.registration = registration;
        // 竞态补漏：调度器可能在 schedule() 返回之前就执行了首次续期（真实线程与测试替身都可能如此）。
        // 若那次续期已失败并置位 renewalStopped，stopRenewal() 当时读到的 registration 还是 null，
        // 不会注销本次句柄；这里补一次 cancel，否则重复任务会一直留在调度器里直到应用关闭
        // （对已释放/已失去的锁持续做存储往返，且引用 state 使其无法回收）。
        if (state.renewalStopped.get()) {
            registration.cancel();
        }
    }

    /**
     * 一次续期：成功即返回；失败/异常都记 WARN 并停止续期，绝不静默继续假装持锁。
     *
     * <p>「续期返回 false」表示锁已不属于自己（过期或他人接管），没有异常可记录；「续期抛异常」
     * 表示存储不可用，按规范记录完整堆栈。</p>
     *
     * @param key   锁键
     * @param state 持锁状态
     * @param lock  注解（取 ttl 用于续期与告警文案）
     */
    private void renewGuarded(String key, ReentrantState state, DistributedLock lock) {
        if (state.renewalStopped.get()) {
            return;
        }
        boolean renewed;
        try {
            renewed = lockService.renew(key, state.owner, Duration.ofSeconds(lock.ttl()));
        } catch (RuntimeException ex) {
            stopRenewal(state);
            log.warn("[ypbin-starter] 分布式锁续期异常，已停止续期（锁可能已被其它实例接管或存储不可用）："
                + "key={}, ttl={}s", key, lock.ttl(), ex);
            return;
        }
        if (renewed) {
            return;
        }
        if (state.renewalStopped.get()) {
            // 与「方法退出」的竞态：解锁本就在进行，不是真的失去锁，不该误报
            return;
        }
        stopRenewal(state);
        log.warn("[ypbin-starter] 分布式锁续期失败：锁已不属于本持有者（可能已过期或被其它实例接管），"
            + "已停止续期；本方法剩余执行时间不再受互斥保护：key={}, ttl={}s", key, lock.ttl());
    }

    /**
     * 停止续期并注销调度（幂等）。
     *
     * @param state 持锁状态
     */
    private void stopRenewal(ReentrantState state) {
        if (state.renewalStopped.compareAndSet(false, true)) {
            Registration registration = state.registration;
            if (registration != null) {
                registration.cancel();
            }
        }
    }

    /**
     * 退出一次加锁：仅当**最外层**（深度归零）才停止续期、真正释放锁并清理 {@link ThreadLocal}。
     *
     * <p>清理先于释放：即使 {@code unlock} 抛异常（如 Redis 不可达），也不会把 owner/深度残留在线程池
     * 复用的线程上——否则该线程下一次调用同 key 会被误判为「重入」而跳过加锁，静默失去互斥。</p>
     *
     * @param key   解析后的锁键
     * @param state 本线程的持有状态
     * @param lock  注解（取 ttl 用于告警文案）
     */
    private void exitGuarded(String key, ReentrantState state, DistributedLock lock) {
        if (--state.depth > 0) {
            return;
        }
        // 先停续期再释放：解锁后不再对同一 key 做无谓的存储往返
        stopRenewal(state);
        Map<String, ReentrantState> held = localLocks.get();
        if (held != null) {
            held.remove(key);
            if (held.isEmpty()) {
                localLocks.remove();
            }
        }
        if (!lockService.unlock(key, state.owner)) {
            // 释放失败通常意味着锁已因 TTL 过期（方法执行超过 ttl），提示业务方调大 ttl 或开启 autoRenew
            log.warn("[ypbin-starter] 释放分布式锁失败（可能已超时过期）：key={}, ttl={}s", key, lock.ttl());
        }
    }

    /**
     * 取本线程对指定 key 的持有状态；未持有（非重入）时返回 {@code null}。
     *
     * @param key 解析后的锁键
     * @return 持有状态或 {@code null}
     */
    @Nullable
    private ReentrantState currentState(String key) {
        Map<String, ReentrantState> held = localLocks.get();
        return held == null ? null : held.get(key);
    }

    /**
     * 取本线程的持有表（惰性创建）。
     *
     * @return 持有表
     */
    private Map<String, ReentrantState> heldLocks() {
        Map<String, ReentrantState> held = localLocks.get();
        if (held == null) {
            held = new HashMap<>();
            localLocks.set(held);
        }
        return held;
    }

    private boolean acquire(String key, String owner, Duration ttl, DistributedLock lock) throws InterruptedException {
        if (lockService.tryLock(key, owner, ttl)) {
            return true;
        }
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(lock.waitTime()).toMillis();
        while (true) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                return false;
            }
            // 不超额睡眠：剩余时间不足一个重试间隔时只睡剩余时间
            Thread.sleep(Math.min(lock.retryInterval(), remaining));
            if (lockService.tryLock(key, owner, ttl)) {
                return true;
            }
        }
    }

    private String buildKey(ProceedingJoinPoint point, DistributedLock lock) {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        String raw = lock.key();
        String suffix;
        if (raw.isBlank()) {
            // 用目标类真实名，避免 CGLIB 代理下拿到 xxx$$SpringCGLIB$$ 名称
            suffix = point.getTarget().getClass().getName() + "#" + method.getName();
        } else {
            suffix = SpelKeyResolver.resolve(raw, method, point.getArgs());
        }
        return "ypbin:lock:" + suffix;
    }

    /**
     * 一次已持有锁的重入状态。
     *
     * <p>{@code depth} 只由持锁线程自身读写（{@link ThreadLocal} 保证可见性边界），故无需同步；
     * {@code renewalStopped} 与 {@code registration} 会被调度线程读/写，故用 volatile 语义。</p>
     */
    private static final class ReentrantState {

        /** 本次（最外层）加锁时的 owner，释放时用于校验，防止误删他人的锁。 */
        private final String owner;

        /** 重入深度：1 表示仅最外层持有。 */
        private int depth;

        /** 续期是否已停止（正常退出或续期失败都会置位）。 */
        private final AtomicBoolean renewalStopped = new AtomicBoolean();

        /** 续期注册句柄；未开启 {@code autoRenew} 时为 {@code null}。 */
        @Nullable
        private volatile Registration registration;

        private ReentrantState(String owner) {
            this.owner = owner;
        }
    }
}

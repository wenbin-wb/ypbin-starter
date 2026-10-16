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

import cn.ypbin.starter.tools.support.SpelKeyResolver;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
 * <p><strong>注意 TTL 语义未变（接入方必读）</strong>：重入期间不会延长 TTL（本切面不提供续期），
 * <strong>方法执行时间应显著短于 {@code ttl}</strong>。若外层方法执行超过 {@code ttl}，锁仍会在存储中
 * 过期，内层重入调用<strong>不再校验</strong>、直接进入临界区（互斥失效）；需要跑得比 TTL 更久的任务
 * 请改用 {@link LeaseLockGuard} 的租约锁（可续期、抢不到即跳过），不要靠调大 {@code ttl} 硬撑。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
@Aspect
public class DistributedLockAspect {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockAspect.class);

    private final LockService lockService;

    /**
     * 本线程当前持有的锁（解析后的 key → owner + 重入深度）。
     *
     * <p>按切面实例隔离（等价于按 {@link LockService} 隔离）：只有同一把逻辑锁才判重入，避免两个
     * 不同锁服务上的同名 key 被误判。用普通 {@link ThreadLocal} 而非 {@code withInitial}：绝大多数
     * 调用并非重入，不该为每次加锁都分配一个 Map（惰性创建见 {@link #heldLocks()}）。</p>
     */
    private final ThreadLocal<Map<String, ReentrantState>> localLocks = new ThreadLocal<>();

    public DistributedLockAspect(LockService lockService) {
        this.lockService = lockService;
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
            return point.proceed();
        } finally {
            exitGuarded(key, state, distributedLock);
        }
    }

    /**
     * 退出一次加锁：仅当**最外层**（深度归零）才真正释放锁并清理 {@link ThreadLocal}。
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
        Map<String, ReentrantState> held = localLocks.get();
        if (held != null) {
            held.remove(key);
            if (held.isEmpty()) {
                localLocks.remove();
            }
        }
        if (!lockService.unlock(key, state.owner)) {
            // 释放失败通常意味着锁已因 TTL 过期（方法执行超过 ttl），提示业务方调大 ttl
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
     * <p>{@code depth} 只由持锁线程自身读写（{@link ThreadLocal} 保证可见性边界），故无需同步。</p>
     */
    private static final class ReentrantState {

        /** 本次（最外层）加锁时的 owner，释放时用于校验，防止误删他人的锁。 */
        private final String owner;

        /** 重入深度：1 表示仅最外层持有。 */
        private int depth;

        private ReentrantState(String owner) {
            this.owner = owner;
        }
    }
}

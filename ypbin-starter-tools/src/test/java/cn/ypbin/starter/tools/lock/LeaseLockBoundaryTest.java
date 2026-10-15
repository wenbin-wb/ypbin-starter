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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 租约锁的两处**边界语义**单测（由下游迁移验收补齐）。
 *
 * <p>背景：本能力自 ypbin-iot 业务仓上收时，那侧原有两条用例在 starter 侧**没有等价覆盖**——
 * ①「空锁（{@code ypbin.lock.enabled=false}）永远抢到且续期/释放无害」；②「存储层自身异常必须
 * **如实上抛**，不得被吞成『没抢到』」。二者都不是生产主路径，但正是它们决定了两类故障时的
 * 可观测性：前者决定"显式关锁"是否真能退回加锁前行为，后者决定"Redis 挂了"是被看见还是被伪装成
 * 「别的实例在跑」（后者会让任务静默停摆且没有任何告警）。</p>
 *
 * <p>断言用「语义等价 + 显式失败」而非实现细节：空锁用公开 API 断言、异常用自定义 store 注入。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
class LeaseLockBoundaryTest {

    /** 固定时钟（不真等）。 */
    private static final Clock CLOCK = Clock.fixed(
        java.time.Instant.ofEpochMilli(1_000_000L), ZoneOffset.UTC);

    @Test
    @DisplayName("空锁：enabled=false 的 Noop 实现「永远抢到」，续期恒成功、释放无动作且不抛")
    void noopLeaseShouldAlwaysBeHeld() {
        NoopLeaseLockService noop = new NoopLeaseLockService();

        LockLease first = noop.tryAcquire("task-a");
        LockLease second = noop.tryAcquire("task-a");

        // 「永远抢到」= 退回加锁之前的行为（每个副本都会执行）——这是 enabled=false 的**定义**，
        // 故第二个请求也必须拿到租约；若哪天改成互斥，说明语义漂移了。
        assertThat(first).as("空锁必须永远抢到").isNotNull();
        assertThat(second).as("空锁不是互斥锁：同一键的第二个请求同样拿到租约").isNotNull();
        assertThat(first.lockKey()).isEqualTo("task-a");
        assertThat(first.isHeld()).as("空锁恒为「持有」").isTrue();
        assertThat(first.expiresAtMillis()).as("空锁无到期概念").isEqualTo(Long.MAX_VALUE);

        assertThat(first.renew()).as("空锁续期恒成功").isTrue();
        assertThatCode(first::release).as("空锁释放必须无动作且不抛").doesNotThrowAnyException();
        assertThat(first.isHeld()).as("空锁释放后仍为持有（无状态）").isTrue();

        // try-with-resources 也必须安全（AutoCloseable 路径）
        assertThatCode(() -> {
            try (LockLease lease = noop.tryAcquire("task-b")) {
                assertThat(lease.isHeld()).isTrue();
            }
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("守卫：空锁下的任务照常执行（executed=true 且非降级），与 enabled=true 的「抢不到就跳过」区分")
    void guardShouldRunTaskUnderNoopLease() {
        LeaseLockProperties properties = new LeaseLockProperties();
        properties.setTtl(Duration.ofSeconds(60));
        properties.setRenewInterval(Duration.ofSeconds(20));
        LeaseLockGuard guard = new LeaseLockGuard(
            new NoopLeaseLockService(), new ScheduledLeaseRenewalScheduler(), properties,
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        java.util.concurrent.atomic.AtomicInteger runs = new java.util.concurrent.atomic.AtomicInteger();
        boolean executed = guard.runExclusively("task-a", runs::incrementAndGet);

        assertThat(executed).as("空锁不互斥 ⇒ 任务必须照常执行").isTrue();
        assertThat(runs.get()).as("任务恰好执行一次").isEqualTo(1);
    }

    @Test
    @DisplayName("存储异常：store 自身抛错时 tryAcquire **如实上抛**，不吞成「没抢到」")
    void storeFailureShouldPropagateInsteadOfLookingLikeNotAcquired() {
        LeaseLockProperties properties = new LeaseLockProperties();
        properties.setTtl(Duration.ofSeconds(60));
        properties.setRenewInterval(Duration.ofSeconds(20));

        LeaseLockStore failing = new LeaseLockStore() {
            @Override
            public boolean tryLock(String key, String token, long expireAtMillis, long nowMillis,
                                   long ttlMillis) {
                throw new IllegalStateException("redis down");
            }

            @Override
            public boolean renew(String key, String token, long newExpireAtMillis, long ttlMillis) {
                return false;
            }

            @Override
            public boolean unlock(String key, String token) {
                return false;
            }
        };

        LeaseLockServiceImpl lock = new LeaseLockServiceImpl(failing, properties, CLOCK);

        // 关键区分：返回 null（= 别的实例在跑，本轮跳过、属正常）vs 抛异常（= 存储不可用，必须被看见）。
        // 若把异常吞成 null，多副本下 Redis 故障会让所有副本都「安静地跳过」，任务实际停摆而无告警。
        assertThatThrownBy(() -> lock.tryAcquire("task-a"))
            .as("存储不可用必须如实上抛，由守卫按 fail-open/fail-closed 决策")
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("redis down");
    }
}

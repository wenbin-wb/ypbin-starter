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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link LeaseLockGuard} 单测：抢不到=跳过、存储异常 fail-open/fail-closed 两分支、
 * finally 必释放、看门狗续期与指标计数。
 *
 * <p>看门狗用**同步调度器**替身（立即执行一次并捕获任务），因此断言的是「守卫是否把续期任务
 * 正确挂上看门狗、并在结束时取消」这一契约，不依赖真实线程与等待。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
class LeaseLockGuardTest {

    private final LeaseLockService leaseLockService = mock(LeaseLockService.class);

    private final LeaseLockProperties properties = new LeaseLockProperties();

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final CapturingScheduler scheduler = new CapturingScheduler();

    private final LeaseLockGuard guard =
        new LeaseLockGuard(leaseLockService, scheduler, properties, meterRegistry);

    @Test
    @DisplayName("抢不到锁 ⇒ 跳过：executed=false、任务不执行、不报错、记 skipped 计数")
    void skipWhenLockHeldByOthers() {
        when(leaseLockService.tryAcquire(any())).thenReturn(null);
        AtomicBoolean ran = new AtomicBoolean(false);

        boolean executed = guard.runExclusively("task-a", () -> ran.set(true));

        assertThat(executed).as("抢不到锁 = 跳过，不是异常").isFalse();
        assertThat(ran).as("未获得锁时任务绝不能执行").isFalse();
        assertThat(counter("ypbin.lock.skipped")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("抢到锁 ⇒ 执行任务、finally 释放租约、取消看门狗")
    void executeAndReleaseWhenAcquired() {
        LockLease lease = mock(LockLease.class);
        when(leaseLockService.tryAcquire("ypbin:lease-lock:task-a")).thenReturn(lease);
        when(lease.renew()).thenReturn(true);

        String result = guard.callExclusively("task-a", () -> "done").result();

        assertThat(result).isEqualTo("done");
        assertThat(scheduler.scheduled.get()).as("必须挂上看门狗").isEqualTo(1);
        assertThat(scheduler.cancelled.get()).as("任务结束必须取消看门狗").isEqualTo(1);
        verify(lease).release();
    }

    @Test
    @DisplayName("任务抛异常时也必须释放租约（finally 语义，锁绝不泄漏）")
    void releaseEvenWhenTaskThrows() {
        LockLease lease = mock(LockLease.class);
        when(leaseLockService.tryAcquire(any())).thenReturn(lease);

        assertThatThrownBy(() -> guard.runExclusively("task-a", () -> {
            throw new IllegalStateException("任务炸了");
        })).isInstanceOf(IllegalStateException.class);

        verify(lease).release();
        assertThat(scheduler.cancelled.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("存储不可用 + fail-open（默认）⇒ 继续执行并标记 degraded")
    void failOpenShouldContinueWhenStoreUnavailable() {
        when(leaseLockService.tryAcquire(any())).thenThrow(new IllegalStateException("redis down"));
        AtomicBoolean ran = new AtomicBoolean(false);

        LeaseLockOutcome<Boolean> outcome = guard.callExclusively("task-a", () -> {
            ran.set(true);
            return Boolean.TRUE;
        });

        assertThat(ran).as("fail-open 时必须继续执行（宁可多跑一轮也不让任务停摆）").isTrue();
        assertThat(outcome.executed()).isTrue();
        assertThat(outcome.degraded()).as("降级执行必须被标记出来，调用方可观测").isTrue();
        assertThat(counter("ypbin.lock.unavailable")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("存储不可用 + fail-closed ⇒ 原样上抛，任务不执行")
    void failClosedShouldRethrowWhenStoreUnavailable() {
        properties.setFailOpen(false);
        when(leaseLockService.tryAcquire(any())).thenThrow(new IllegalStateException("redis down"));
        AtomicBoolean ran = new AtomicBoolean(false);

        assertThatThrownBy(() -> guard.runExclusively("task-a", () -> ran.set(true)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("redis down");

        assertThat(ran).as("fail-closed 时绝不能假装执行过").isFalse();
        assertThat(counter("ypbin.lock.skipped")).isZero();
    }

    @Test
    @DisplayName("看门狗：续期成功不计数；续期失败记 renew.failed（失败不抛）")
    void watchdogShouldCountRenewFailures() {
        LockLease lease = mock(LockLease.class);
        when(leaseLockService.tryAcquire(any())).thenReturn(lease);
        when(lease.renew()).thenReturn(false);

        guard.runExclusively("task-a", () -> {
            // 任务执行期间由调度器替身同步触发一次续期
            scheduler.runScheduled();
        });

        assertThat(counter("ypbin.lock.renew.failed"))
            .as("续期失败必须计数（否则锁存储抖动不可见）")
            .isEqualTo(1.0);
        verify(lease).release();
    }

    @Test
    @DisplayName("指标：前缀可配，不写死任何宿主域名；按 task tag 区分")
    void metricsShouldUseConfigurablePrefix() {
        properties.setMetricPrefix("acme.custom.lock");
        when(leaseLockService.tryAcquire(any())).thenReturn(null);

        guard.runExclusively("task-a", () -> {
        });

        assertThat(counter("acme.custom.lock.skipped")).isEqualTo(1.0);
        assertThat(meterRegistry.find("ypbin.lock.skipped").counter())
            .as("配置了新前缀后不应再产生默认前缀的指标")
            .isNull();
    }

    /**
     * 取指定名称计数器的值（不存在时返回 0）。
     *
     * @param name 指标名
     * @return 计数值
     */
    private double counter(String name) {
        var counter = meterRegistry.find(name).counter();
        return counter == null ? 0.0 : counter.count();
    }

    /** 同步调度器替身：记录挂载/取消次数，并可手动触发已挂载的续期任务。 */
    private static final class CapturingScheduler implements LeaseRenewalScheduler {

        private final AtomicInteger scheduled = new AtomicInteger();

        private final AtomicInteger cancelled = new AtomicInteger();

        private Runnable task;

        @Override
        public Cancellable schedule(Runnable runnable, Duration interval) {
            this.task = runnable;
            scheduled.incrementAndGet();
            return cancelled::incrementAndGet;
        }

        /** 同步执行一次已挂载的续期任务（替身真实的定时触发）。 */
        void runScheduled() {
            task.run();
        }
    }
}

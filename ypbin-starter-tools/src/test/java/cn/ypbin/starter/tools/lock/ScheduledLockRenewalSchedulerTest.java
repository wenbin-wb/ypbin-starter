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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ScheduledLockRenewalScheduler} 的真实线程语义测试。
 *
 * <p>覆盖切面测试无法覆盖的三件事：任务真的按间隔执行、注销后真的不再执行（无泄漏）、
 * {@code destroy} 关停后不再接受注册（应用关闭不静默丢弃续期）。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
class ScheduledLockRenewalSchedulerTest {

    private static final Duration SHORT_INTERVAL = Duration.ofMillis(20);

    @Test
    @DisplayName("注册后按间隔重复执行；注销后停止且登记被移除")
    void scheduleRepeatsAndCancelDeregisters() throws Exception {
        ScheduledLockRenewalScheduler scheduler = new ScheduledLockRenewalScheduler();
        try {
            AtomicInteger runs = new AtomicInteger();
            LockRenewalScheduler.Registration registration = scheduler.schedule(runs::incrementAndGet, SHORT_INTERVAL);

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (runs.get() == 0 && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }
            assertThat(runs.get()).as("续期任务必须真的被调度执行").isPositive();
            assertThat(scheduler.activeRegistrations()).isEqualTo(1);

            registration.cancel();
            registration.cancel();
            assertThat(scheduler.activeRegistrations()).as("注销后登记必须移除（防句柄堆积）").isZero();

            // 等一次在途执行落定后再取基线，随后确认计数不再增长
            Thread.sleep(SHORT_INTERVAL.toMillis() * 3);
            int afterCancel = runs.get();
            Thread.sleep(SHORT_INTERVAL.toMillis() * 3);
            assertThat(runs.get()).as("注销后不得再执行（无泄漏）").isEqualTo(afterCancel);
        } finally {
            scheduler.destroy();
        }
    }

    @Test
    @DisplayName("destroy 注销全部登记并关停线程（应用关闭不留续期任务）")
    void destroyStopsAllRegistrations() {
        ScheduledLockRenewalScheduler scheduler = new ScheduledLockRenewalScheduler();
        AtomicInteger runs = new AtomicInteger();

        scheduler.schedule(runs::incrementAndGet, Duration.ofSeconds(1));
        scheduler.schedule(runs::incrementAndGet, Duration.ofSeconds(1));
        assertThat(scheduler.activeRegistrations()).isEqualTo(2);

        scheduler.destroy();

        assertThat(scheduler.activeRegistrations()).isZero();
        assertThatThrownBy(() -> scheduler.schedule(runs::incrementAndGet, Duration.ofSeconds(1)))
            .as("关停后再注册必须显式失败，不得静默丢弃续期（那会表现为「莫名不再续期」）")
            .isInstanceOf(RejectedExecutionException.class);
    }

    @Test
    @DisplayName("非正间隔拒绝（0/负/空都会让调度器行为不可预期）")
    void nonPositiveIntervalRejected() {
        ScheduledLockRenewalScheduler scheduler = new ScheduledLockRenewalScheduler();
        try {
            assertThatThrownBy(() -> scheduler.schedule(() -> { }, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> scheduler.schedule(() -> { }, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> scheduler.schedule(() -> { }, null))
                .isInstanceOf(IllegalArgumentException.class);
            assertThat(scheduler.activeRegistrations()).isZero();
        } finally {
            scheduler.destroy();
        }
    }

    @Test
    @DisplayName("续期线程是 daemon：应用退出不被它拖住")
    void renewalThreadIsDaemon() throws Exception {
        ScheduledLockRenewalScheduler scheduler = new ScheduledLockRenewalScheduler();
        try {
            CountDownLatch started = new CountDownLatch(1);
            scheduler.schedule(started::countDown, SHORT_INTERVAL);
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

            Thread renewalThread = Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> thread.getName().equals(ScheduledLockRenewalScheduler.THREAD_NAME))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到续期线程"));
            assertThat(renewalThread.isDaemon()).isTrue();
        } finally {
            scheduler.destroy();
        }
    }
}

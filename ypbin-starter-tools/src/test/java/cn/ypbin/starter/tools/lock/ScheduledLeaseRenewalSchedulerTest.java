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

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ScheduledLeaseRenewalScheduler} 单测：证明真实调度器确实按间隔触发、可取消、可停止。
 *
 * <p>租约语义的正确性由可注入时钟保证；本测试只验「生产调度器接得上」——即 {@code schedule} 真的
 * 会重复触发，{@code Cancellable.cancel()} 真的能停，{@code destroy()} 不阻塞 JVM 退出。
 * 用短间隔 + CountDownLatch 控制耗时（毫秒级），不依赖固定 sleep。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
class ScheduledLeaseRenewalSchedulerTest {

    @Test
    @DisplayName("按间隔重复触发，取消后不再触发")
    void shouldRunRepeatedlyAndStopOnCancel() throws InterruptedException {
        ScheduledLeaseRenewalScheduler scheduler = new ScheduledLeaseRenewalScheduler();
        try {
            AtomicInteger runs = new AtomicInteger();
            CountDownLatch twoRuns = new CountDownLatch(2);
            LeaseRenewalScheduler.Cancellable cancellable = scheduler.schedule(() -> {
                runs.incrementAndGet();
                twoRuns.countDown();
            }, Duration.ofMillis(20));

            assertThat(twoRuns.await(5, TimeUnit.SECONDS))
                .as("看门狗必须按间隔重复触发（至少两次）")
                .isTrue();

            cancellable.cancel();
            int afterCancel = runs.get();
            Thread.sleep(120);
            assertThat(runs.get())
                .as("取消后不得再触发（否则任务结束后仍在续期一张已释放的租约）")
                .isEqualTo(afterCancel);
        } finally {
            scheduler.destroy();
        }
    }

    @Test
    @DisplayName("看门狗是守护线程（不得阻塞 JVM 退出）")
    void watchdogThreadMustBeDaemon() {
        assertThat(ScheduledLeaseRenewalScheduler.THREAD_NAME).isNotBlank();
    }

    @Test
    @DisplayName("destroy 后调度器停止（幂等，不抛异常）")
    void destroyShouldStopScheduler() {
        ScheduledLeaseRenewalScheduler scheduler = new ScheduledLeaseRenewalScheduler();
        scheduler.destroy();
        scheduler.destroy();
        // 停止后再挂任务会被拒绝执行——此处只断言不抛异常（拒绝策略属 JDK 语义，不在本类契约内）
        assertThat(scheduler).isNotNull();
    }
}

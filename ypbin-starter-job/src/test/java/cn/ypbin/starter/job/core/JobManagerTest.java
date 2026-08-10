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
package cn.ypbin.starter.job.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;

/**
 * {@link JobManager} 调度、手动触发、集群防重、执行体路由测试。
 *
 * @author wenbin
 * @since 2026-08-01
 */
class JobManagerTest {

    private static final CronService CRON_SERVICE = new SpringCronService();

    private ThreadPoolTaskScheduler scheduler;

    /** 永远抢到锁 */
    private static final JobManager.JobLock ALWAYS = new JobManager.JobLock() {
        @Override
        public boolean tryLock(String key, String owner, Duration ttl) {
            return true;
        }

        @Override
        public boolean unlock(String key, String owner) {
            return true;
        }
    };

    /** 永远抢不到锁（模拟其它节点持锁） */
    private static final JobManager.JobLock NEVER = new JobManager.JobLock() {
        @Override
        public boolean tryLock(String key, String owner, Duration ttl) {
            return false;
        }

        @Override
        public boolean unlock(String key, String owner) {
            return true;
        }
    };

    @BeforeEach
    void setUp() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.initialize();
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    private JobDefinition fixedRate(long id, String executor, long seconds) {
        JobDefinition def = new JobDefinition();
        def.setId(id);
        def.setName("job-" + id);
        def.setExecutor(executor);
        def.setFixedRateSeconds(seconds);
        return def;
    }

    @Test
    void fixedRateJobExecutesAndCallsListener() {
        AtomicInteger runs = new AtomicInteger();
        AtomicInteger success = new AtomicInteger();
        Map<String, JobHandler> handlers = Map.of("demo", ctx -> runs.incrementAndGet());
        JobExecutionListener listener = new JobExecutionListener() {
            @Override
            public void onSuccess(JobContext context, long durationMs) {
                success.incrementAndGet();
            }
        };
        JobManager manager = new JobManager("node-1", scheduler, handlers, listener, ALWAYS, CRON_SERVICE);

        manager.register(fixedRate(1L, "demo", 1));
        await().atMost(Duration.ofSeconds(3)).until(() -> runs.get() >= 1);
        assertThat(success.get()).isGreaterThanOrEqualTo(1);

        manager.unregister(1L);
        assertThat(manager.isScheduled(1L)).isFalse();
    }

    @Test
    void triggerNowExecutesImmediately() {
        AtomicInteger runs = new AtomicInteger();
        Map<String, JobHandler> handlers = Map.of("demo", ctx -> runs.incrementAndGet());
        JobManager manager = new JobManager("node-1", scheduler, handlers,
            new JobExecutionListener() {
            }, ALWAYS, CRON_SERVICE);

        JobDefinition def = fixedRate(2L, "demo", 3600);
        manager.triggerNow(def);
        await().atMost(Duration.ofSeconds(2)).until(() -> runs.get() == 1);
    }

    @Test
    void concurrentGuardSkipsWhenLockNotAcquired() {
        AtomicInteger runs = new AtomicInteger();
        AtomicInteger skips = new AtomicInteger();
        Map<String, JobHandler> handlers = Map.of("demo", ctx -> runs.incrementAndGet());
        JobExecutionListener listener = new JobExecutionListener() {
            @Override
            public void onSkip(JobContext context) {
                skips.incrementAndGet();
            }
        };
        // 抢不到锁：不执行、回调 onSkip
        JobManager manager = new JobManager("node-1", scheduler, handlers, listener, NEVER, CRON_SERVICE);

        manager.triggerNow(fixedRate(3L, "demo", 3600));
        await().atMost(Duration.ofSeconds(2)).until(() -> skips.get() == 1);
        assertThat(runs.get()).isZero();
    }

    @Test
    void missingExecutorIsRejectedBeforeScheduling() {
        JobManager manager = new JobManager("node-1", scheduler, Map.of(),
            new JobExecutionListener() {
            }, ALWAYS, CRON_SERVICE);
        JobDefinition definition = fixedRate(4L, "notExist", 3600);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> manager.register(definition))
            .withMessageContaining("任务执行器不存在");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> manager.triggerNow(definition))
            .withMessageContaining("任务执行器不存在");
        assertThat(manager.isScheduled(4L)).isFalse();
    }

    @Test
    void registerReplacesOnReRegister() {
        Map<String, JobHandler> handlers = Map.of("demo", ctx -> {
        });
        JobManager manager = new JobManager("node-1", scheduler, handlers,
            new JobExecutionListener() {
            }, ALWAYS, CRON_SERVICE);

        manager.register(fixedRate(5L, "demo", 3600));
        manager.register(fixedRate(5L, "demo", 1800)); // 重复注册应替换而非报错
        assertThat(manager.scheduledIds()).containsExactly(5L);
    }

    @Test
    void rejectsInvalidCronBeforeRegistration() {
        JobManager manager = manager(scheduler);
        JobDefinition definition = new JobDefinition(6L, "invalid", "demo", "0 0 25 * * ?");

        assertThatIllegalArgumentException()
            .isThrownBy(() -> manager.register(definition))
            .withMessageContaining("Cron 表达式不合法");
        assertThat(manager.isScheduled(6L)).isFalse();
    }

    @Test
    void replacementFailureKeepsPreviousScheduleActive() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> previousFuture = mock(ScheduledFuture.class);
        doReturn(previousFuture)
            .doThrow(new IllegalStateException("schedule failed"))
            .when(taskScheduler)
            .schedule(any(Runnable.class), any(Trigger.class));
        JobManager manager = manager(taskScheduler);

        manager.register(fixedRate(7L, "demo", 3600));

        assertThatThrownBy(() -> manager.replace(fixedRate(7L, "demo", 1800)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("schedule failed");
        assertThat(manager.isScheduled(7L)).isTrue();
        assertThat(manager.scheduledIds()).containsExactly(7L);
        verify(previousFuture, never()).cancel(false);
    }

    @Test
    void fixedRateRegistrationUsesFixedRateTrigger() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future)
            .when(taskScheduler)
            .schedule(any(Runnable.class), any(Trigger.class));
        JobManager manager = manager(taskScheduler);

        manager.register(fixedRate(8L, "demo", 30));

        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(taskScheduler).schedule(any(Runnable.class), triggerCaptor.capture());
        assertThat(triggerCaptor.getValue()).isInstanceOf(PeriodicTrigger.class);
        assertThat(((PeriodicTrigger)triggerCaptor.getValue()).isFixedRate()).isTrue();
    }

    @Test
    void rejectsMissingOrMultipleTriggerDefinitions() {
        JobManager manager = manager(scheduler);
        JobDefinition missing = fixedRate(9L, "demo", 0);
        JobDefinition multiple = fixedRate(10L, "demo", 30);
        multiple.setCron("0 * * * * *");

        assertThatIllegalArgumentException()
            .isThrownBy(() -> manager.register(missing))
            .withMessageContaining("须且只能指定");
        assertThatIllegalArgumentException()
            .isThrownBy(() -> manager.register(multiple))
            .withMessageContaining("须且只能指定");
    }

    @Test
    void validatesDefinitionWithoutRegisteringSchedule() {
        JobManager manager = manager(scheduler);
        JobDefinition definition = fixedRate(17L, "demo", 30);

        manager.validateDefinition(definition);

        assertThat(manager.isScheduled(17L)).isFalse();
    }

    @Test
    void reconcileAddsUpdatesAndRemovesSchedules() {
        JobManager manager = manager(scheduler);
        manager.register(fixedRate(11L, "demo", 3600));
        manager.register(fixedRate(12L, "demo", 3600));

        manager.reconcile(List.of(
            fixedRate(11L, "demo", 1800),
            fixedRate(13L, "demo", 3600)));

        assertThat(manager.scheduledIds()).containsExactlyInAnyOrder(11L, 13L);
    }

    @Test
    void reconcileFailureKeepsEntirePreviousRegistry() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> firstPrevious = mock(ScheduledFuture.class);
        ScheduledFuture<?> secondPrevious = mock(ScheduledFuture.class);
        ScheduledFuture<?> candidate = mock(ScheduledFuture.class);
        doReturn(firstPrevious, secondPrevious, candidate)
            .doThrow(new IllegalStateException("schedule failed"))
            .when(taskScheduler)
            .schedule(any(Runnable.class), any(Trigger.class));
        JobManager manager = manager(taskScheduler);
        manager.register(fixedRate(14L, "demo", 3600));
        manager.register(fixedRate(15L, "demo", 3600));

        assertThatThrownBy(() -> manager.reconcile(List.of(
            fixedRate(14L, "demo", 1800),
            fixedRate(15L, "demo", 3600),
            fixedRate(16L, "demo", 3600))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("schedule failed");

        assertThat(manager.scheduledIds()).containsExactlyInAnyOrder(14L, 15L);
        verify(firstPrevious, never()).cancel(false);
        verify(secondPrevious, never()).cancel(false);
        verify(candidate).cancel(false);
    }

    private JobManager manager(TaskScheduler taskScheduler) {
        return new JobManager("node-1", taskScheduler, Map.of("demo", context -> {
        }), new JobExecutionListener() {
        }, ALWAYS, CRON_SERVICE);
    }
}

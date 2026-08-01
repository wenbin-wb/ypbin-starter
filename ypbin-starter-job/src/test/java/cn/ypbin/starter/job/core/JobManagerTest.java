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
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * {@link JobManager} 调度、手动触发、集群防重、执行体路由测试。
 *
 * @author wenbin
 * @since 2026-08-01
 */
class JobManagerTest {

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
        JobManager manager = new JobManager("node-1", scheduler, handlers, listener, ALWAYS);

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
            }, ALWAYS);

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
        JobManager manager = new JobManager("node-1", scheduler, handlers, listener, NEVER);

        manager.triggerNow(fixedRate(3L, "demo", 3600));
        await().atMost(Duration.ofSeconds(2)).until(() -> skips.get() == 1);
        assertThat(runs.get()).isZero();
    }

    @Test
    void missingExecutorCallsOnError() {
        AtomicReference<Throwable> error = new AtomicReference<>();
        JobExecutionListener listener = new JobExecutionListener() {
            @Override
            public void onError(JobContext context, long durationMs, Throwable e) {
                error.set(e);
            }
        };
        JobManager manager = new JobManager("node-1", scheduler, Map.of(), listener, ALWAYS);

        manager.triggerNow(fixedRate(4L, "notExist", 3600));
        await().atMost(Duration.ofSeconds(2)).until(() -> error.get() != null);
        assertThat(error.get()).hasMessageContaining("执行器不存在");
    }

    @Test
    void registerReplacesOnReRegister() {
        Map<String, JobHandler> handlers = Map.of("demo", ctx -> {
        });
        JobManager manager = new JobManager("node-1", scheduler, handlers,
            new JobExecutionListener() {
            }, ALWAYS);

        manager.register(fixedRate(5L, "demo", 3600));
        manager.register(fixedRate(5L, "demo", 1800)); // 重复注册应替换而非报错
        assertThat(manager.scheduledIds()).containsExactly(5L);
    }
}

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
package cn.ypbin.starter.async;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.async.autoconfigure.AsyncAnnotationAutoConfiguration;
import cn.ypbin.starter.async.autoconfigure.AsyncAutoConfiguration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link AsyncAutoConfiguration} 与 {@link AsyncAnnotationAutoConfiguration} 装配测试。
 *
 * @author wenbin
 * @since 2026-08-15
 */
class AsyncAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AsyncAutoConfiguration.class));

    private final ApplicationContextRunner annotationRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            AsyncAutoConfiguration.class, AsyncAnnotationAutoConfiguration.class));

    /** 默认虚拟线程模式：executor 为 SimpleAsyncTaskExecutor，scheduler 为 ThreadPoolTaskScheduler。 */
    @Test
    void shouldRegisterVirtualThreadExecutorByDefault() {
        runner.run(context -> {
            assertThat(context).hasBean("ypbinTaskExecutor");
            assertThat(context).hasBean("ypbinTaskScheduler");
            assertThat(context).hasSingleBean(AsyncUncaughtExceptionHandler.class);
            assertThat(context.getBean("ypbinTaskExecutor")).isInstanceOf(SimpleAsyncTaskExecutor.class);
            assertThat(context.getBean("ypbinTaskScheduler")).isInstanceOf(ThreadPoolTaskScheduler.class);
        });
    }

    /** 显式关闭虚拟线程时使用平台线程池，池化参数生效。 */
    @Test
    void shouldUsePlatformThreadPoolWhenVirtualThreadsDisabled() {
        runner.withPropertyValues("ypbin.async.virtual-threads=false",
                "ypbin.async.core-size=3", "ypbin.async.max-size=6",
                "ypbin.async.thread-name-prefix=test-async-")
            .run(context -> {
                assertThat(context.getBean("ypbinTaskExecutor")).isInstanceOf(ThreadPoolTaskExecutor.class);
                ThreadPoolTaskExecutor executor =
                    context.getBean("ypbinTaskExecutor", ThreadPoolTaskExecutor.class);
                assertThat(executor.getCorePoolSize()).isEqualTo(3);
                assertThat(executor.getMaxPoolSize()).isEqualTo(6);
                assertThat(executor.getThreadNamePrefix()).isEqualTo("test-async-");
            });
    }

    /** ypbin.async.enabled=false 时整体不装配。 */
    @Test
    void shouldBackOffWhenDisabled() {
        runner.withPropertyValues("ypbin.async.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean("ypbinTaskExecutor"));
    }

    // ------------------------------------------------------------------ 虚拟线程执行器停机接线

    /**
     * 默认配置（await-termination=true、await-termination-seconds=30）下，虚拟线程执行器的终止等待
     * 被接为 {@code 30 * 1000 = 30000} 毫秒。
     *
     * <p>{@code SimpleAsyncTaskExecutor#setTaskTerminationTimeout} 单位是毫秒（Spring Framework 7.0.9
     * 官方源码 Javadoc：「the timeout in milliseconds」，{@code close()} 中以 {@code threads.wait(timeout)}
     * 消费该值），且该字段无 getter，故此处用反射断言私有字段。
     */
    @Test
    void virtualThreadExecutorShouldWireTerminationTimeoutInMillis() {
        runner.run(context -> {
            SimpleAsyncTaskExecutor executor = executor(context);
            assertThat(ReflectionTestUtils.getField(executor, "taskTerminationTimeout")).isEqualTo(30_000L);
        });
    }

    /** 秒 → 毫秒换算：配置 2 秒生效为 2000 毫秒；配置 0 表示不等待（与平台线程池语义一致）。 */
    @Test
    void virtualThreadExecutorShouldScaleTerminationTimeoutToMillis() {
        runner.withPropertyValues("ypbin.async.await-termination-seconds=2").run(context ->
            assertThat(ReflectionTestUtils.getField(executor(context), "taskTerminationTimeout"))
                .isEqualTo(2_000L));
        runner.withPropertyValues("ypbin.async.await-termination-seconds=0").run(context ->
            assertThat(ReflectionTestUtils.getField(executor(context), "taskTerminationTimeout"))
                .isEqualTo(0L));
    }

    /** await-termination=false 时不等待，且与平台线程池 shutdownNow() 对齐：关闭立即中断在途线程。 */
    @Test
    void virtualThreadExecutorShouldCancelRemainingTasksWhenAwaitDisabled() {
        runner.withPropertyValues("ypbin.async.await-termination=false").run(context -> {
            SimpleAsyncTaskExecutor executor = executor(context);
            assertThat(ReflectionTestUtils.getField(executor, "cancelRemainingTasksOnClose")).isEqualTo(true);
            assertThat(ReflectionTestUtils.getField(executor, "taskTerminationTimeout")).isEqualTo(0L);
        });
    }

    /**
     * 行为验证：{@code close()} 确实按毫秒等待在途任务（配置 1 秒 → 实际等待约 1 秒后中断剩余线程）。
     *
     * <p>若单位被误当作秒（等待 1 秒 × 1000）或纳秒（立即返回），实测等待时长会数量级偏离断言区间。
     */
    @Test
    void virtualThreadExecutorShouldAwaitInFlightTaskOnClose() {
        runner.withPropertyValues("ypbin.async.await-termination-seconds=1").run(context -> {
            SimpleAsyncTaskExecutor executor = executor(context);
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            executor.execute(() -> {
                started.countDown();
                awaitQuietly(release);
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

            long begin = System.nanoTime();
            executor.close();
            long elapsedMillis = (System.nanoTime() - begin) / 1_000_000L;
            release.countDown();

            assertThat(elapsedMillis).isGreaterThanOrEqualTo(500L).isLessThan(5_000L);
        });
    }

    private static SimpleAsyncTaskExecutor executor(AssertableApplicationContext context) {
        return context.getBean("ypbinTaskExecutor", SimpleAsyncTaskExecutor.class);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** enable-annotation=false 时不注册 AsyncConfigurer，@Async 接管关闭。 */
    @Test
    void shouldNotRegisterAsyncConfigurerWhenAnnotationDisabled() {
        annotationRunner.withPropertyValues("ypbin.async.enable-annotation=false")
            .run(context -> assertThat(context).doesNotHaveBean(AsyncConfigurer.class));
    }

    /** enable-annotation=true（默认）时注册 AsyncConfigurer。 */
    @Test
    void shouldRegisterAsyncConfigurerByDefault() {
        annotationRunner.run(context ->
            assertThat(context).hasSingleBean(AsyncConfigurer.class));
    }
}

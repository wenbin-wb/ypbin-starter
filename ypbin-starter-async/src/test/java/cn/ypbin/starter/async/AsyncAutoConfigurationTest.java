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
import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

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

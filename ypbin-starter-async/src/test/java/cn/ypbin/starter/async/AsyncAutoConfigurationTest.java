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

import cn.ypbin.starter.async.autoconfigure.AsyncAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * {@link AsyncAutoConfiguration} 装配测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class AsyncAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AsyncAutoConfiguration.class));

    @Test
    void shouldRegisterExecutorAndSchedulerByDefault() {
        runner.run(context -> {
            assertThat(context).hasBean("ypbinTaskExecutor");
            assertThat(context).hasBean("ypbinTaskScheduler");
            assertThat(context).hasSingleBean(AsyncUncaughtExceptionHandler.class);
            assertThat(context.getBean("ypbinTaskExecutor")).isInstanceOf(ThreadPoolTaskExecutor.class);
            assertThat(context.getBean("ypbinTaskScheduler")).isInstanceOf(ThreadPoolTaskScheduler.class);
        });
    }

    @Test
    void shouldApplyCustomPoolProperties() {
        runner.withPropertyValues("ypbin.async.core-size=3", "ypbin.async.max-size=6",
                "ypbin.async.thread-name-prefix=test-async-")
            .run(context -> {
                ThreadPoolTaskExecutor executor = context.getBean("ypbinTaskExecutor", ThreadPoolTaskExecutor.class);
                assertThat(executor.getCorePoolSize()).isEqualTo(3);
                assertThat(executor.getMaxPoolSize()).isEqualTo(6);
                assertThat(executor.getThreadNamePrefix()).isEqualTo("test-async-");
            });
    }

    @Test
    void shouldBackOffWhenDisabled() {
        runner.withPropertyValues("ypbin.async.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean("ypbinTaskExecutor"));
    }
}

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
package cn.ypbin.starter.core.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.Retryable;

/**
 * {@link ResilienceAutoConfiguration} 测试：验证 Spring 7 原生 {@code @Retryable} 在装配后真实生效，
 * 且可通过 {@code ypbin.resilience.enabled=false} 关闭。
 *
 * @author wenbin
 * @since 2026-09-13
 */
class ResilienceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ResilienceAutoConfiguration.class))
        .withUserConfiguration(RetryTargetConfig.class);

    @Test
    void retryableShouldRetryUntilSuccess() {
        contextRunner.run(context -> {
            RetryTarget target = context.getBean(RetryTarget.class);
            assertThat(target.call()).isEqualTo("ok");
            // 前两次失败、第三次成功 → 重试机制确实生效
            assertThat(target.attempts()).isEqualTo(3);
        });
    }

    @Test
    void retryableShouldNotRetryWhenDisabled() {
        contextRunner.withPropertyValues("ypbin.resilience.enabled=false").run(context -> {
            RetryTarget target = context.getBean(RetryTarget.class);
            // 未启用时注解不生效：首次异常直接抛出，不做重试
            assertThatThrownBy(target::call).isInstanceOf(IllegalStateException.class);
            assertThat(target.attempts()).isEqualTo(1);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class RetryTargetConfig {

        @Bean
        RetryTarget retryTarget() {
            return new RetryTarget();
        }
    }

    /** 测试用目标：前两次抛异常，第三次返回成功。 */
    static class RetryTarget {

        private final AtomicInteger counter = new AtomicInteger();

        @Retryable(maxRetries = 3, delay = 10)
        public String call() {
            int attempt = counter.incrementAndGet();
            if (attempt < 3) {
                throw new IllegalStateException("瞬时故障 " + attempt);
            }
            return "ok";
        }

        int attempts() {
            return counter.get();
        }
    }
}

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
package cn.ypbin.starter.security.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.security.password.lock.InMemoryPasswordAttemptStore;
import cn.ypbin.starter.security.password.lock.PasswordAttemptStore;
import cn.ypbin.starter.security.password.lock.RedisPasswordAttemptStore;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link SecurityAutoConfiguration} 装配测试。
 *
 * <p>锁定密码错误计数存储「可选 Redis 依赖」的两种场景，防回归：</p>
 * <ul>
 *     <li><strong>无 Redis</strong>：用 {@link FilteredClassLoader} 隐藏 {@link StringRedisTemplate}，复现轻量消费端。
 *     配置类内省不得触碰 Redis 类型（否则 {@code NoClassDefFoundError} 启动即崩），且装配内存实现。</li>
 *     <li><strong>有 Redis</strong>：提供 {@link StringRedisTemplate} Bean，装配的应是 Redis 实现而非内存兜底
 *     （验证 {@code @Import} 保证 Redis 先于内存兜底注册）。</li>
 * </ul>
 *
 * @author wenbin
 * @since 2026-08-07
 */
class SecurityAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class));

    @Test
    void withoutRedis_startsAndUsesInMemoryStore() {
        runner.withClassLoader(new FilteredClassLoader(StringRedisTemplate.class))
            .run(context -> {
                // 关键：无 Redis 时上下文能正常启动（不因配置类内省触碰 StringRedisTemplate 而崩）
                assertThat(context).hasNotFailed();
                assertThat(context).getBean(PasswordAttemptStore.class).isInstanceOf(InMemoryPasswordAttemptStore.class);
            });
    }

    @Test
    void withRedis_usesRedisStore() {
        runner.withUserConfiguration(RedisTemplateConfig.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                // @Import 保证 Redis 实现先注册，内存兜底的 @ConditionalOnMissingBean 退让
                assertThat(context).getBean(PasswordAttemptStore.class).isInstanceOf(RedisPasswordAttemptStore.class);
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class RedisTemplateConfig {
        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return Mockito.mock(StringRedisTemplate.class);
        }
    }
}

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
package cn.ypbin.starter.tools.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.tools.idempotent.IdempotentStore;
import cn.ypbin.starter.tools.idempotent.InMemoryIdempotentStore;
import cn.ypbin.starter.tools.idempotent.RedisIdempotentStore;
import cn.ypbin.starter.tools.limiter.InMemoryRateLimiterStore;
import cn.ypbin.starter.tools.limiter.RateLimiterStore;
import cn.ypbin.starter.tools.limiter.RedisRateLimiterStore;
import cn.ypbin.starter.tools.lock.InMemoryLockService;
import cn.ypbin.starter.tools.lock.LockService;
import cn.ypbin.starter.tools.lock.RedisLockService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link ToolsAutoConfiguration} 装配测试。
 *
 * <p>锁定「可选 Redis 依赖」的两种场景，防回归：</p>
 * <ul>
 *     <li><strong>无 Redis</strong>：用 {@link FilteredClassLoader} 隐藏 {@link StringRedisTemplate}，复现轻量消费端。
 *     配置类内省不得触碰 Redis 类型（否则 {@code NoClassDefFoundError} 启动即崩），且装配内存实现。</li>
 *     <li><strong>有 Redis</strong>：提供 {@link StringRedisTemplate} Bean，装配的应是 Redis 实现而非内存兜底
 *     （验证 {@code @Import} 保证 Redis 先于内存兜底注册）。</li>
 * </ul>
 *
 * @author wenbin
 * @since 2026-08-03
 */
class ToolsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ToolsAutoConfiguration.class));

    @Test
    void withoutRedis_startsAndUsesInMemoryStores() {
        runner.withClassLoader(new FilteredClassLoader(StringRedisTemplate.class))
            .run(context -> {
                // 关键：无 Redis 时上下文能正常启动（不因配置类内省触碰 StringRedisTemplate 而崩）
                assertThat(context).hasNotFailed();
                assertThat(context).getBean(RateLimiterStore.class).isInstanceOf(InMemoryRateLimiterStore.class);
                assertThat(context).getBean(IdempotentStore.class).isInstanceOf(InMemoryIdempotentStore.class);
                assertThat(context).getBean(LockService.class).isInstanceOf(InMemoryLockService.class);
            });
    }

    @Test
    void withRedis_usesRedisStores() {
        runner.withUserConfiguration(RedisTemplateConfig.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                // @Import 保证 Redis 实现先注册，内存兜底的 @ConditionalOnMissingBean 退让
                assertThat(context).getBean(RateLimiterStore.class).isInstanceOf(RedisRateLimiterStore.class);
                assertThat(context).getBean(IdempotentStore.class).isInstanceOf(RedisIdempotentStore.class);
                assertThat(context).getBean(LockService.class).isInstanceOf(RedisLockService.class);
            });
    }

    @Test
    void withRedisButDistributedDisabled_usesInMemoryForRateLimitAndIdempotent() {
        runner.withUserConfiguration(RedisTemplateConfig.class)
            .withPropertyValues(
                "ypbin.tools.rate-limit.distributed=false",
                "ypbin.tools.idempotent.distributed=false")
            .run(context -> {
                assertThat(context).hasNotFailed();
                // distributed=false 强制走内存实现
                assertThat(context).getBean(RateLimiterStore.class).isInstanceOf(InMemoryRateLimiterStore.class);
                assertThat(context).getBean(IdempotentStore.class).isInstanceOf(InMemoryIdempotentStore.class);
                // 锁无 distributed 开关，有 Redis 即用 Redis
                assertThat(context).getBean(LockService.class).isInstanceOf(RedisLockService.class);
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class RedisTemplateConfig {
        @Bean
        StringRedisTemplate stringRedisTemplate() {
            // 装配阶段不触发真实连接，用 mock 即可满足类型注入
            return Mockito.mock(StringRedisTemplate.class);
        }
    }
}

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
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
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

    /**
     * ★ 顺序回归（2026-10-15 线上事故暴露的同类陷阱）：与 Boot 的 Redis 自动配置一起装配时，
     * Redis 分支必须被真正选中。
     *
     * <p><b>为什么单靠上面 {@code withRedis_usesRedisStores} 不够</b>：那条用例把一个用户自定义的
     * {@code StringRedisTemplate} Bean 直接塞进上下文，Bean 定义在自动配置处理<em>之前</em>就已存在，
     * 因此无论本配置的排序如何都会命中 Redis 分支——它测不出顺序问题。而真实部署里模板由 Boot 的
     * {@code DataRedisAutoConfiguration} 注册，属于<b>另一个自动配置</b>：</p>
     *
     * <ul>
     *   <li>{@code @ConditionalOnBean} 只看得见「先于它处理完」的 Bean 定义；</li>
     *   <li>本模块包名 {@code cn.ypbin.*} 在 Boot 的自动配置排序中排在 {@code org.springframework.*}
     *       <b>之前</b>（无显式顺序时按类名字典序，且 tools 模块的自动配置此前没有任何
     *       {@code @AutoConfigureAfter}）；</li>
     *   <li>⇒ {@code RedisStoreConfiguration} 求值时模板尚未注册 ⇒ 判定「没有 Redis」⇒
     *       <b>静默退化为内存实现</b>（分布式限流/幂等/锁全部失效，且没有任何报错）。</li>
     * </ul>
     *
     * <p>修法是给 {@link ToolsAutoConfiguration} 加类级 {@code @AutoConfigureAfter(DataRedisAutoConfiguration)}。
     * 实测：修前 {@code RateLimiterStore} 落到 {@code InMemoryRateLimiterStore}，修后为
     * {@code RedisRateLimiterStore}。</p>
     */
    @Test
    void withRealBootRedisAutoConfiguration_usesRedisStores() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                DataRedisAutoConfiguration.class, ToolsAutoConfiguration.class))
            .withUserConfiguration(RedisConnectionFactoryConfig.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.containsBean("stringRedisTemplate"))
                    .as("前置条件：Boot 的 Redis 自动配置已注册模板")
                    .isTrue();
                assertThat(context.getBean(RateLimiterStore.class))
                    .as("有 Redis 时必须选 Redis 实现：退化为内存意味着限流在多副本下形同虚设")
                    .isInstanceOf(RedisRateLimiterStore.class);
                assertThat(context.getBean(IdempotentStore.class)).isInstanceOf(RedisIdempotentStore.class);
                assertThat(context.getBean(LockService.class)).isInstanceOf(RedisLockService.class);
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

    /** 真实 Boot Redis 自动配置所需的连接工厂（mock 即可，装配阶段不建连）。 */
    @Configuration(proxyBeanMethods = false)
    static class RedisConnectionFactoryConfig {
        @Bean
        org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory() {
            return Mockito.mock(org.springframework.data.redis.connection.RedisConnectionFactory.class);
        }
    }
}

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
package cn.ypbin.starter.sign.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.sign.core.InMemoryNonceStore;
import cn.ypbin.starter.sign.core.NonceStore;
import cn.ypbin.starter.sign.core.RedisNonceStore;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link SignAutoConfiguration} 装配测试。
 *
 * <p>锁定 nonce 存储「可选 Redis 依赖」的两种场景，防回归：</p>
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
class SignAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SignAutoConfiguration.class))
        .withPropertyValues("ypbin.sign.enabled=true");

    @Test
    void withoutRedis_startsAndUsesInMemoryStore() {
        runner.withClassLoader(new FilteredClassLoader(StringRedisTemplate.class))
            .run(context -> {
                // 关键：无 Redis 时上下文能正常启动（不因配置类内省触碰 StringRedisTemplate 而崩）
                assertThat(context).hasNotFailed();
                assertThat(context).getBean(NonceStore.class).isInstanceOf(InMemoryNonceStore.class);
            });
    }

    @Test
    void withRedis_usesRedisStore() {
        runner.withUserConfiguration(RedisTemplateConfig.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                // @Import 保证 Redis 实现先注册，内存兜底的 @ConditionalOnMissingBean 退让
                assertThat(context).getBean(NonceStore.class).isInstanceOf(RedisNonceStore.class);
            });
    }

    /**
     * ★ 顺序回归（2026-10-15 租约锁线上事故排查时发现的同类陷阱）：与 Boot 的 Redis 自动配置一起
     * 装配时，Redis 分支必须被真正选中。
     *
     * <p>{@code withRedis_usesRedisStore} 把模板 Bean 直接放进用户配置，其 Bean 定义在自动配置处理
     * <em>之前</em>就存在，因此测不出顺序问题；真实部署里模板由 Boot 的
     * {@code DataRedisAutoConfiguration} 注册，而 {@code @ConditionalOnBean} 只看得见「先于它处理完」的
     * Bean 定义、本模块 {@code cn.ypbin.*} 又排在 {@code org.springframework.*} 之前 ⇒ 条件恒假 ⇒
     * nonce 静默退化为进程内（多副本防重放失效且无报错）。修法：类级
     * {@code @AutoConfigureAfter(DataRedisAutoConfiguration)}。</p>
     */
    @Test
    void withRealBootRedisAutoConfiguration_usesRedisStore() {
        new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                DataRedisAutoConfiguration.class, SignAutoConfiguration.class))
            .withPropertyValues("ypbin.sign.enabled=true")
            .withUserConfiguration(RedisConnectionFactoryConfig.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.containsBean("stringRedisTemplate"))
                    .as("前置条件：Boot 的 Redis 自动配置已注册模板")
                    .isTrue();
                assertThat(context.getBean(NonceStore.class))
                    .as("有 Redis 时必须选 Redis 实现：退化为进程内意味着多副本防重放失效")
                    .isInstanceOf(RedisNonceStore.class);
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class RedisTemplateConfig {
        @Bean
        StringRedisTemplate stringRedisTemplate() {
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

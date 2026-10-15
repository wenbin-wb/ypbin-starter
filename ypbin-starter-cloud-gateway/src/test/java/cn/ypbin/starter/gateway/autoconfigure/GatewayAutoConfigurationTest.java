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
package cn.ypbin.starter.gateway.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.gateway.filter.HeaderSanitizeGlobalFilter;
import cn.ypbin.starter.gateway.filter.RequestIdGlobalFilter;
import cn.ypbin.starter.gateway.handler.GatewayExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.web.cors.reactive.CorsWebFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link GatewayAutoConfiguration} 自动配置装配测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class GatewayAutoConfigurationTest {

    private final ReactiveWebApplicationContextRunner runner = new ReactiveWebApplicationContextRunner()
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withConfiguration(AutoConfigurations.of(GatewayAutoConfiguration.class));

    @Test
    void shouldRegisterDefaultBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RequestIdGlobalFilter.class);
            assertThat(context).hasSingleBean(HeaderSanitizeGlobalFilter.class);
            assertThat(context).hasSingleBean(GatewayExceptionHandler.class);
            assertThat(context).hasSingleBean(GatewayProperties.class);
        });
    }

    @Test
    void shouldNotRegisterCorsFilterByDefault() {
        runner.run(context -> assertThat(context).doesNotHaveBean(CorsWebFilter.class));
    }

    @Test
    void shouldRegisterCorsFilterWhenEnabled() {
        runner.withPropertyValues("ypbin.gateway.cors.enabled=true")
            .run(context -> assertThat(context).hasSingleBean(CorsWebFilter.class));
    }

    @Test
    void shouldBackOffWhenGatewayDisabled() {
        runner.withPropertyValues("ypbin.gateway.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(RequestIdGlobalFilter.class);
                assertThat(context).doesNotHaveBean(GatewayExceptionHandler.class);
            });
    }

    @Test
    void shouldDisableHeaderSanitizeWhenTurnedOff() {
        runner.withPropertyValues("ypbin.gateway.header-sanitize.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(HeaderSanitizeGlobalFilter.class));
    }

    /**
     * ★ 顺序回归（2026-10-15 用真实 Boot 自动配置组合发现，并已由
     * {@code AutoConfigurationOrderingTest} 门禁拦下）：宿主引入 reactive Redis 且显式打开限流开关时，
     * 限流过滤器<b>必须真的装配</b>。
     *
     * <p>此前的用例只把模板 Bean 作为用户配置塞进上下文，Bean 定义在自动配置处理<em>之前</em>就存在，
     * 因此测不出顺序问题；真实部署里模板由 Boot 的 {@code DataRedisReactiveAutoConfiguration} 注册，
     * 而 {@code @ConditionalOnBean} 只看得见「先于它处理完」的 Bean 定义、本模块
     * {@code cn.ypbin.*} 又排在 {@code org.springframework.*} 之前 ⇒ 条件恒假 ⇒
     * <b>过滤器整块不装配，网关静默不限流</b>。修法：类级
     * {@code @AutoConfigureAfter(DataRedisReactiveAutoConfiguration)}。</p>
     */
    @Test
    void withRealBootReactiveRedisAutoConfiguration_registersRateLimitFilter() {
        new ReactiveWebApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withConfiguration(AutoConfigurations.of(
                org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration.class,
                GatewayAutoConfiguration.class))
            .withPropertyValues("ypbin.gateway.rate-limit.enabled=true")
            .withUserConfiguration(ReactiveRedisStubConfiguration.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.containsBean("reactiveStringRedisTemplate"))
                    .as("前置条件：Boot 的 reactive Redis 自动配置已注册模板")
                    .isTrue();
                assertThat(context.containsBean("attributeRateLimitGlobalFilter"))
                    .as("有 reactive Redis 且开关打开时限流过滤器必须装配：否则网关静默不限流")
                    .isTrue();
            });
    }

    /** 真实 Boot reactive Redis 自动配置所需的连接工厂（mock 即可，装配阶段不建连）。 */
    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    static class ReactiveRedisStubConfiguration {

        @org.springframework.context.annotation.Bean
        org.springframework.data.redis.connection.ReactiveRedisConnectionFactory reactiveRedisConnectionFactory() {
            return org.mockito.Mockito.mock(
                org.springframework.data.redis.connection.ReactiveRedisConnectionFactory.class);
        }
    }
}

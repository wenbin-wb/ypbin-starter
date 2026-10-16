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
package cn.ypbin.starter.gateway.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.alibaba.nacos.api.config.ConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link NacosRouteAutoConfiguration} 与 Spring Cloud Gateway 自动配置的<b>顺序回归</b>。
 *
 * <p><b>为什么必须用「真实 Boot 自动配置组合」而不是用户配置塞 Bean</b>：
 * {@code @ConditionalOnBean} 只看得见<em>先于它处理完</em>的 Bean 定义。此前的用例若把
 * {@code RouteDefinitionLocator}/{@code RouteDefinitionWriter} 作为用户配置 Bean 塞进上下文，
 * Bean 定义在自动配置处理<em>之前</em>就存在，顺序问题被完全掩盖。真实部署里这两个 Bean 由
 * Spring Cloud Gateway 的 {@link GatewayAutoConfiguration} 注册，而本仓
 * {@code cn.ypbin.*} 在 Boot 排序里排在 {@code org.springframework.*} <b>之前</b> ⇒ 条件恒假 ⇒
 * {@code NacosRouteInitializer} 静默不装配。</p>
 *
 * <p>本用例把 {@code GatewayAutoConfiguration} 与 {@link NacosRouteAutoConfiguration} 一起放进
 * {@code AutoConfigurations}，让 Boot 的 {@code AutoConfigurationSorter} 决定处理顺序，
 * 断言「宿主开了开关 + 有 Nacos ConfigService + 有路由存储」时初始化器<b>必须真的装配</b>。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
class NacosRouteAutoConfigurationOrderingTest {

    /**
     * Gateway 自动配置装配期所需的支撑 Bean。
     *
     * <p>本用例的目标是<b>顺序</b>，不是网关运行时：只需让 {@code GatewayAutoConfiguration}
     * 能完成装配、把路由存储与 {@code RouteDefinitionLocator} 真正注册出来即可，故这些
     * 与路由存储无关的依赖以最小桩提供（不建连、不发请求）。</p>
     */
    private ReactiveWebApplicationContextRunner runner() {
        return new ReactiveWebApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(ConfigService.class, () -> mock(ConfigService.class))
            .withBean(org.springframework.boot.web.server.autoconfigure.ServerProperties.class,
                org.springframework.boot.web.server.autoconfigure.ServerProperties::new)
            .withBean(org.springframework.boot.ssl.SslBundles.class,
                () -> mock(org.springframework.boot.ssl.SslBundles.class))
            .withBean(org.springframework.http.codec.ServerCodecConfigurer.class,
                org.springframework.http.codec.ServerCodecConfigurer::create)
            .withBean(org.springframework.boot.webflux.autoconfigure.WebFluxProperties.class,
                org.springframework.boot.webflux.autoconfigure.WebFluxProperties::new)
            .withConfiguration(AutoConfigurations.of(
                GatewayAutoConfiguration.class,
                NacosRouteAutoConfiguration.class));
    }

    @Test
    @DisplayName("★ 真实 Boot 自动配置组合下：Nacos 开关打开时必须装配初始化器（否则动态路由静默失效）")
    void withRealGatewayAutoConfiguration_registersNacosRouteInitializer() {
        runner()
            .withPropertyValues("ypbin.gateway.route.nacos.enabled=true")
            .run(context -> {
                assertThat(context).hasNotFailed();

                // 前置条件自证：这两个 Bean 确实由 Gateway 的自动配置注册（不是我们塞的用户 Bean）。
                // 用 (a) 只可见先处理完的定义——若 NacosRouteAutoConfiguration 未声明顺序，
                // 下面这条断言会失败并指出条件恒假，即本门禁的判据所在。
                assertThat(context).hasBean("inMemoryRouteDefinitionRepository");
                assertThat(context).hasBean("propertiesRouteDefinitionLocator");
                assertThat(context.getBeanNamesForType(RouteDefinitionLocator.class)).isNotEmpty();
                assertThat(context.getBeanNamesForType(RouteDefinitionWriter.class)).isNotEmpty();

                assertThat(context.containsBean("nacosRouteInitializer"))
                    .as("宿主已提供 Nacos ConfigService、路由存储齐备且 ypbin.gateway.route.nacos.enabled=true ⇒ "
                        + "NacosRouteInitializer 必须装配；不装配意味着 Nacos 动态路由静默不生效"
                        + "（修法：类级 @AutoConfigureAfter(GatewayAutoConfiguration)）")
                    .isTrue();
                assertThat(context).hasSingleBean(NacosRouteInitializer.class);
            });
    }

    @Test
    @DisplayName("开关未打开时不装配（默认关闭语义不变）")
    void disabledByDefault_doesNotRegisterInitializer() {
        runner()
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(NacosRouteInitializer.class);
            });
    }
}

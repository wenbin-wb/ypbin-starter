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
}

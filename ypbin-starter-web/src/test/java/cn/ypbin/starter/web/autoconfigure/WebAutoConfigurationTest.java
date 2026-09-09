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
package cn.ypbin.starter.web.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.web.handler.GlobalExceptionHandler;
import cn.ypbin.starter.web.request.RepeatableReadRequestFilter;
import cn.ypbin.starter.web.request.RepeatableReadRequestWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CorsFilter;

/**
 * {@link WebAutoConfiguration} 自动配置装配测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class WebAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(WebAutoConfiguration.class));

    @Test
    void shouldRegisterGlobalExceptionHandlerInServletWeb() {
        runner.run(context -> assertThat(context).hasSingleBean(GlobalExceptionHandler.class));
    }

    @Test
    void shouldNotRegisterCorsFilterByDefault() {
        runner.run(context -> assertThat(context).doesNotHaveBean(CorsFilter.class));
    }

    @Test
    void shouldRegisterCorsFilterWhenEnabled() {
        runner.withPropertyValues("ypbin.web.cors.enabled=true")
            .run(context -> assertThat(context).hasSingleBean(CorsFilter.class));
    }

    @Test
    void shouldBackOffWhenCustomExceptionHandlerProvided() {
        runner.withUserConfiguration(CustomHandlerConfig.class)
            .run(context -> {
                assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
                assertThat(context.getBean(GlobalExceptionHandler.class))
                    .isInstanceOf(CustomExceptionHandler.class);            });
    }

    @Test
    void shouldNotApplyInReactiveWebApplication() {
        new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebAutoConfiguration.class))
            .run(context -> assertThat(context).doesNotHaveBean(GlobalExceptionHandler.class));
    }

    @Test
    void xssPropertiesShouldExposeDefaults() {
        XssProperties props = new XssProperties();
        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getExcludes()).isEmpty();
        assertThat(XssProperties.PREFIX).isEqualTo("ypbin.web.xss");
    }

    @Test
    void repeatableReadPropertiesShouldExposeDefaults() {
        RepeatableReadProperties props = new RepeatableReadProperties();
        assertThat(RepeatableReadProperties.PREFIX).isEqualTo("ypbin.web.repeatable-read");
        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getMaxBodyBytes())
            .isEqualTo(RepeatableReadRequestWrapper.DEFAULT_MAX_BODY_BYTES);
    }

    @Test
    void shouldNotRegisterRepeatableReadFilterByDefault() {
        runner.run(context -> {
            var filters = context.getBeansOfType(FilterRegistrationBean.class).values();
            assertThat(filters).noneSatisfy(
                frb -> assertThat(frb.getFilter()).isInstanceOf(RepeatableReadRequestFilter.class));
        });
    }

    @Test
    void shouldRegisterRepeatableReadFilterWhenEnabled() {
        runner.withPropertyValues("ypbin.web.repeatable-read.enabled=true")
            .run(context -> {
                var filters = context.getBeansOfType(FilterRegistrationBean.class).values();
                assertThat(filters).anySatisfy(
                    frb -> assertThat(frb.getFilter()).isInstanceOf(RepeatableReadRequestFilter.class));
                assertThat(context).hasSingleBean(RepeatableReadProperties.class);
            });
    }

    @Test
    void shouldBindRepeatableReadMaxBodyBytesFromProperty() {
        runner.withPropertyValues(
                "ypbin.web.repeatable-read.enabled=true",
                "ypbin.web.repeatable-read.max-body-bytes=2048")
            .run(context -> assertThat(context.getBean(RepeatableReadProperties.class).getMaxBodyBytes())
                .isEqualTo(2048L));
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomHandlerConfig {
        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new CustomExceptionHandler();
        }
    }

    static class CustomExceptionHandler extends GlobalExceptionHandler {
    }
}

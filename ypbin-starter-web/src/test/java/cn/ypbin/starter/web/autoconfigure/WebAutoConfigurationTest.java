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
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
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
                    .isInstanceOf(CustomExceptionHandler.class);
            });
    }

    @Test
    void shouldNotApplyInReactiveWebApplication() {
        new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebAutoConfiguration.class))
            .run(context -> assertThat(context).doesNotHaveBean(GlobalExceptionHandler.class));
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

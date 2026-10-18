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

import cn.ypbin.starter.web.enums.BaseEnumConverterFactory;
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
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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

    // ------------------------------------------------------------------ BaseEnum 参数绑定开关

    /**
     * 关闭 {@code ypbin.web.base-enum.enabled} 时上下文必须能正常启动，且两个 Bean 都不存在。
     *
     * <p><b>为什么这条必须有</b>：注册器 Bean
     * （{@code baseEnumFormatterRegistrar}）以 {@code BaseEnumConverterFactory} 作为方法参数，
     * 若「转换器因开关关闭而不装配、注册器却没有同步缺席」，容器会因缺少依赖直接启动失败
     * （{@code NoSuchBeanDefinitionException}）。这类缺陷只在「关开关」这条路径上暴露，
     * 默认路径的用例发现不了——故必须显式钉住 {@code hasNotFailed()}。</p>
     */
    @Test
    void shouldStartWithoutFailureWhenBaseEnumDisabled() {
        runner.withPropertyValues("ypbin.web.base-enum.enabled=false")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(BaseEnumConverterFactory.class);
                assertThat(context).doesNotHaveBean("baseEnumFormatterRegistrar");
                // 关闭 BaseEnum 能力不得连带影响其它 Web 装配
                assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
            });
    }

    /**
     * 默认（未配置）时转换器与注册器都必须存在并生效——与上一条互补，避免只测「关」不测「开」。
     *
     * <p>同时钉住 {@code @ConditionalOnBean} 的求值顺序：若条件在转换器注册之前被求值，
     * 会出现「开关默认开启却静默不装配注册器」的缺陷，此断言即会失败。</p>
     */
    @Test
    void shouldRegisterBaseEnumConverterAndRegistrarByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(BaseEnumConverterFactory.class);
            assertThat(context).hasBean("baseEnumFormatterRegistrar");
            // 注册器必须以 MVC 配置器形态存在，否则 addFormatters 不会被回调、参数绑定实际不生效
            assertThat(context.getBean("baseEnumFormatterRegistrar"))
                .isInstanceOf(WebMvcConfigurer.class);
        });
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

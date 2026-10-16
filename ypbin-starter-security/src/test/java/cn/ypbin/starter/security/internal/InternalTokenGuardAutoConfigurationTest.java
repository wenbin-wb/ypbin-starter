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
package cn.ypbin.starter.security.internal;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.security.satoken.SecurityExcludePathProvider;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

/**
 * {@link InternalTokenGuardAutoConfiguration} 装配回归测试。
 *
 * <p>守护三条容易静默失效的约束：</p>
 * <ol>
 *     <li><b>默认关闭</b>：未显式开启时不得注册任何守卫（不破坏未配置宿主）；</li>
 *     <li><b>RAW_401 空清单不得落到 {@code /*}</b>：Boot 的
 *     {@code AbstractFilterRegistrationBean} 在无 URL 模式时默认映射 {@code /*}，那会让守卫
 *     拦下应用<b>全部</b>请求（可用性事故）——必须显式 {@code setEnabled(false)}；</li>
 *     <li><b>Servlet 专属</b>：WebFlux 应用（网关）不装配。</li>
 * </ol>
 *
 * @author wenbin
 * @since 2026-10-16
 */
@ExtendWith(OutputCaptureExtension.class)
class InternalTokenGuardAutoConfigurationTest {

    private static final String ENABLED = "ypbin.security.internal-token.guard-enabled=true";

    private WebApplicationContextRunner servletRunner() {
        return new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(InternalTokenGuardAutoConfiguration.class));
    }

    @Test
    @DisplayName("默认关闭：未显式开启时不注册守卫，也不注册任何过滤器")
    void disabledByDefault() {
        servletRunner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(InternalTokenGuardWebMvcConfigurer.class);
            assertThat(context).doesNotHaveBean(
                InternalTokenGuardAutoConfiguration.EXCLUDE_PATH_PROVIDER_BEAN_NAME);
            assertThat(context.getBeansOfType(FilterRegistrationBean.class)).isEmpty();
        });
    }

    @Test
    @DisplayName("显式关闭同样不注册")
    void explicitlyDisabledRegistersNothing() {
        servletRunner().withPropertyValues("ypbin.security.internal-token.guard-enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(InternalTokenGuardWebMvcConfigurer.class);
                assertThat(context.getBeansOfType(FilterRegistrationBean.class)).isEmpty();
            });
    }

    @Test
    @DisplayName("开启：注册 ENVELOPE 拦截器配置与 RAW_401 过滤器注册")
    void enabledRegistersBothSemantics() {
        servletRunner().withPropertyValues(ENABLED, "ypbin.security.internal-token.token=s3cr3t")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(InternalTokenGuardWebMvcConfigurer.class);
                assertThat(context).hasBean(
                    InternalTokenGuardAutoConfiguration.RAW_401_REGISTRATION_BEAN_NAME);
            });
    }

    @Test
    @DisplayName("★ RAW_401 未配置路径：注册 Bean 必须停用（否则 Boot 兜底映射 /* 会拦下全应用）")
    void raw401WithoutPathsMustBeDisabledNotMappedToCatchAll() {
        servletRunner().withPropertyValues(ENABLED, "ypbin.security.internal-token.token=s3cr3t")
            .run(context -> {
                FilterRegistrationBean<?> registration = context.getBean(
                    InternalTokenGuardAutoConfiguration.RAW_401_REGISTRATION_BEAN_NAME,
                    FilterRegistrationBean.class);

                assertThat(registration.isEnabled())
                    .as("RAW_401 路径清单为空时必须停用：启用且无 URL 模式会被 Boot 映射到 /*")
                    .isFalse();
                assertThat(registration.getUrlPatterns())
                    .as("绝不允许出现 /* 兜底映射")
                    .doesNotContain("/*")
                    .isEmpty();
            });
    }

    @Test
    @DisplayName("RAW_401 配置路径：按配置生效、顺序为 HIGHEST_PRECEDENCE + 10")
    void raw401WithPathsIsMapped() {
        servletRunner().withPropertyValues(
                ENABLED,
                "ypbin.security.internal-token.token=s3cr3t",
                "ypbin.security.internal-token.raw401-path-patterns=/internal/mqtt/*")
            .run(context -> {
                FilterRegistrationBean<?> registration = context.getBean(
                    InternalTokenGuardAutoConfiguration.RAW_401_REGISTRATION_BEAN_NAME,
                    FilterRegistrationBean.class);

                assertThat(registration.isEnabled()).isTrue();
                assertThat(registration.getUrlPatterns()).containsExactly("/internal/mqtt/*");
                assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 10);
                assertThat(registration.getFilter()).isInstanceOf(InternalTokenGuard.class);
            });
    }

    @Test
    @DisplayName("RAW_401 路径含空白项时按有效项映射；全空白则停用")
    void raw401BlankPatternsHandled() {
        servletRunner().withPropertyValues(
                ENABLED,
                "ypbin.security.internal-token.token=s3cr3t",
                "ypbin.security.internal-token.raw401-path-patterns=/internal/mqtt/*,   ")
            .run(context -> {
                FilterRegistrationBean<?> registration = context.getBean(
                    InternalTokenGuardAutoConfiguration.RAW_401_REGISTRATION_BEAN_NAME,
                    FilterRegistrationBean.class);
                assertThat(registration.isEnabled()).isTrue();
                assertThat(registration.getUrlPatterns()).containsExactly("/internal/mqtt/*");
            });

        servletRunner().withPropertyValues(
                ENABLED,
                "ypbin.security.internal-token.token=s3cr3t",
                "ypbin.security.internal-token.raw401-path-patterns=   ")
            .run(context -> {
                FilterRegistrationBean<?> registration = context.getBean(
                    InternalTokenGuardAutoConfiguration.RAW_401_REGISTRATION_BEAN_NAME,
                    FilterRegistrationBean.class);
                assertThat(registration.isEnabled()).isFalse();
                assertThat(registration.getUrlPatterns()).isEmpty();
            });
    }

    @Test
    @DisplayName("WebFlux 应用（网关）不装配：无 HandlerInterceptor 语义")
    void reactiveWebApplicationDoesNotRegister() {
        new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(InternalTokenGuardAutoConfiguration.class))
            .withPropertyValues(ENABLED, "ypbin.security.internal-token.token=s3cr3t")
            .run(context -> assertThat(context).doesNotHaveBean(InternalTokenGuardWebMvcConfigurer.class));
    }

    @Test
    @DisplayName("配置项可被宿主绑定（凭证/头名/两种路径清单）")
    void propertiesAreBound() {
        servletRunner().withPropertyValues(
                ENABLED,
                "ypbin.security.internal-token.token=s3cr3t",
                "ypbin.security.internal-token.header-name=X-Custom-Token",
                "ypbin.security.internal-token.envelope-path-patterns=/internal/**",
                "ypbin.security.internal-token.raw401-path-patterns=/internal/mqtt/*")
            .run(context -> {
                InternalTokenProperties properties = context.getBean(InternalTokenProperties.class);

                assertThat(properties.isGuardEnabled()).isTrue();
                assertThat(properties.getToken()).isEqualTo("s3cr3t");
                assertThat(properties.getHeaderName()).isEqualTo("X-Custom-Token");
                assertThat(properties.getEnvelopePathPatterns()).containsExactly("/internal/**");
                assertThat(properties.getRaw401PathPatterns()).containsExactly("/internal/mqtt/*");
            });
    }

    // ------------------------------------------------------------------ 静默失效防线（2026-10-17 加固）

    @Test
    @DisplayName("★ Ant 风格 raw401 路径归一化为 Servlet 的 /*（否则容器按字面量匹配、过滤器静默失效）")
    void antStyleRaw401PatternIsNormalizedToServletMapping() {
        servletRunner().withPropertyValues(
                ENABLED,
                "ypbin.security.internal-token.token=s3cr3t",
                "ypbin.security.internal-token.raw401-path-patterns=/internal/mqtt/**")
            .run(context -> {
                assertThat(context).hasNotFailed();
                FilterRegistrationBean<?> registration = context.getBean(
                    InternalTokenGuardAutoConfiguration.RAW_401_REGISTRATION_BEAN_NAME,
                    FilterRegistrationBean.class);

                assertThat(registration.getUrlPatterns())
                    .as("Servlet 规范不认 '**'，必须归一化为 '/internal/mqtt/*'")
                    .containsExactly("/internal/mqtt/*");
            });
    }

    @Test
    @DisplayName("★ 无法被 Servlet 规则解释的通配写法：启动即失败（不得静默退化为字面量匹配）")
    void unmappableWildcardPatternFailsFast() {
        servletRunner().withPropertyValues(
                ENABLED,
                "ypbin.security.internal-token.token=s3cr3t",
                "ypbin.security.internal-token.raw401-path-patterns=/internal/*/mqtt")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("★ 漏写前导斜杠/混合形态的通配写法同样启动失败（否则容器按字面量匹配、过滤器永不触发）")
    void unmappableWildcardShapesFailFast() {
        for (String pattern : List.of("internal/*", "internal/**", "/*/*", "*.json/*", "http://host/*", "*")) {
            servletRunner().withPropertyValues(
                    ENABLED,
                    "ypbin.security.internal-token.token=s3cr3t",
                    "ypbin.security.internal-token.raw401-path-patterns=" + pattern)
                .run(context -> assertThat(context)
                    .as("非法形态必须启动失败：path=%s", pattern)
                    .hasFailed());
        }
    }

    @Test
    @DisplayName("合法 Servlet 形态不得被误拒（路径映射 / 精确路径 / 扩展名映射）")
    void legalServletPatternsAreAccepted() {
        for (String pattern : List.of("/internal/*", "/exact/path", "*.json", "/", "/internal/")) {
            servletRunner().withPropertyValues(
                    ENABLED,
                    "ypbin.security.internal-token.token=s3cr3t",
                    "ypbin.security.internal-token.raw401-path-patterns=" + pattern)
                .run(context -> assertThat(context)
                    .as("合法形态不得被误拒：path=%s", pattern)
                    .hasNotFailed());
        }
    }

    @Test
    @DisplayName("★ 守卫接管路径贡献给 Sa-Token 放行清单（否则 Feign 直连先被登录拦截打回）")
    void guardPathsAreContributedToSaTokenExcludes() {
        servletRunner().withPropertyValues(
                ENABLED,
                "ypbin.security.internal-token.token=s3cr3t",
                "ypbin.security.internal-token.raw401-path-patterns=/internal/mqtt/*")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasBean(
                    InternalTokenGuardAutoConfiguration.EXCLUDE_PATH_PROVIDER_BEAN_NAME);
                SecurityExcludePathProvider provider = context.getBean(
                    InternalTokenGuardAutoConfiguration.EXCLUDE_PATH_PROVIDER_BEAN_NAME,
                    SecurityExcludePathProvider.class);

                assertThat(provider.excludePaths()).containsExactly("/internal/**", "/internal/mqtt/*");
            });
    }

    @Test
    @DisplayName("★ 开启守卫但漏配凭证：启动日志必须显式告警（否则首个内部请求被拒才发现）")
    void missingTokenIsWarnedAtStartup(CapturedOutput output) {
        servletRunner().withPropertyValues(ENABLED).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(output)
                .as("guard-enabled=true 且 token 为空必须在启动日志里可见")
                .contains("已开启但未配置凭证");
        });
    }

    @Test
    @DisplayName("★ 已存在其它同类型贡献者时，守卫的放行贡献者不得被静默挤掉（按 Bean 名判定）")
    void excludeProviderNotShadowedByOtherContributors() {
        servletRunner()
            .withBean("otherExcludePathProvider", SecurityExcludePathProvider.class,
                () -> (SecurityExcludePathProvider) List::of)
            .withPropertyValues(ENABLED, "ypbin.security.internal-token.token=s3cr3t")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context)
                    .as("按类型判定会被其它同类型 Bean 挤掉，必须按名判定")
                    .hasBean(InternalTokenGuardAutoConfiguration.EXCLUDE_PATH_PROVIDER_BEAN_NAME);
            });
    }
}

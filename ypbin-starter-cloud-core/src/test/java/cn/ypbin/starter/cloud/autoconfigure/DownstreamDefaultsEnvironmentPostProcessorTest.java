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
package cn.ypbin.starter.cloud.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.SpringFactoriesLoader;

/**
 * {@link DownstreamDefaultsEnvironmentPostProcessor} 单元测试（#103 验收）。
 *
 * <p><b>本类最重要的一条不是「默认值有没有注入」，而是「安全开关默认不被改宽」</b>。
 * 早期实现默认注入 {@code ypbin.security.interceptor=false}，会使依赖 {@code cloud-core}
 * 却未显式配置该项的既有服务（如 {@code ypbin-auth}）在升级后静默失去全局登录校验——
 * 那是升级即引入的越权风险。#103 验收标准 4「不得改变既有部署行为」正是指这类默认值外溢，
 * 故本测试把「默认不注入」列为第一条断言。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class DownstreamDefaultsEnvironmentPostProcessorTest {

    private static final String SECURITY_INTERCEPTOR =
        DownstreamDefaultsEnvironmentPostProcessor.SECURITY_INTERCEPTOR_PROPERTY;
    private static final String TENANT_FAIL_ON_MISSING =
        DownstreamDefaultsEnvironmentPostProcessor.TENANT_FAIL_ON_MISSING_PROPERTY;
    private static final String SECURITY_OPT_IN =
        DownstreamDefaultsEnvironmentPostProcessor.SECURITY_INTERCEPTOR_DISABLED_OPT_IN;
    private static final String ENABLED =
        DownstreamDefaultsEnvironmentPostProcessor.ENABLED_PROPERTY;
    private static final String TRUSTED_SOURCE_TOKEN = "ypbin.cloud.feign.trusted-source-token";

    private final DownstreamDefaultsEnvironmentPostProcessor processor =
        new DownstreamDefaultsEnvironmentPostProcessor();

    // ------------------------------------------------------------------ 安全红线：默认不注入

    @Test
    @DisplayName("★★ 默认不注入 security.interceptor（既有宿主如 auth 的登录拦截行为不变）")
    void securityInterceptorMustNotBeInjectedByDefault() {
        StandardEnvironment environment = new StandardEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(SECURITY_INTERCEPTOR))
            .as("默认不得关闭全局登录拦截：那会让 auth 等既有宿主静默失去登录校验")
            .isNull();
        MapPropertySource source = (MapPropertySource) environment.getPropertySources()
            .get("ypbinDownstreamDefaults");
        assertThat(source).isNotNull();
        assertThat(source.getPropertyNames())
            .as("安全开关不得出现在默认注入的属性源里")
            .doesNotContain(SECURITY_INTERCEPTOR);
    }

    @Test
    @DisplayName("★ 默认只注入 fail-closed 方向的租户项（方向安全才允许默认）")
    void onlySafeDefaultsShouldBeInjectedByDefault() {
        StandardEnvironment environment = new StandardEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(TENANT_FAIL_ON_MISSING, Boolean.class)).isTrue();
        MapPropertySource source = (MapPropertySource) environment.getPropertySources()
            .get("ypbinDownstreamDefaults");
        assertThat(source).isNotNull();
        assertThat(source.getPropertyNames()).containsExactly(TENANT_FAIL_ON_MISSING);
    }

    // ------------------------------------------------------------------ 显式 opt-in

    @Test
    @DisplayName("★ 显式 opt-in 后才注入 security.interceptor=false")
    void securityInterceptorShouldBeInjectedWhenOptedIn() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("application", Map.of(
            SECURITY_OPT_IN, "true")));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(SECURITY_INTERCEPTOR, Boolean.class)).isFalse();
        assertThat(environment.getProperty(TENANT_FAIL_ON_MISSING, Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("opt-in 显式为 false 时不注入（与默认口径一致）")
    void securityInterceptorShouldNotBeInjectedWhenOptInFalse() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("application", Map.of(
            SECURITY_OPT_IN, "false")));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(SECURITY_INTERCEPTOR)).isNull();
    }

    @Test
    @DisplayName("opt-in 后业务显式配置的 security.interceptor 仍然优先")
    void explicitSecurityConfigShouldWinEvenWhenOptedIn() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("application", Map.of(
            SECURITY_OPT_IN, "true",
            SECURITY_INTERCEPTOR, "true")));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(SECURITY_INTERCEPTOR, Boolean.class)).isTrue();
    }

    // ------------------------------------------------------------------ 业务覆盖

    @Test
    @DisplayName("★ 业务显式配置优先：显式设 fail-on-missing-tenant=false 时不被默认值覆盖")
    void explicitOverrideShouldWin() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("application", Map.of(
            TENANT_FAIL_ON_MISSING, "false")));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(TENANT_FAIL_ON_MISSING, Boolean.class)).isFalse();
    }

    @Test
    @DisplayName("注入位置是最低优先级属性源（业务配置一定覆盖它）")
    void defaultsShouldBeLowestPrecedence() {
        StandardEnvironment environment = new StandardEnvironment();
        // 用与默认项无关的业务配置，保证注入确实发生（否则若业务覆盖了全部默认项，
        // 本类会正确地「不添加属性源」，此时断言属性源存在就没有意义）
        environment.getPropertySources().addFirst(new MapPropertySource("application", Map.of(
            "server.port", "8080")));

        processor.postProcessEnvironment(environment, new SpringApplication());

        List<String> names = new ArrayList<>();
        environment.getPropertySources().forEach(source -> names.add(source.getName()));
        assertThat(names).last().isEqualTo("ypbinDownstreamDefaults");
    }

    @Test
    @DisplayName("业务已覆盖全部默认项时不添加空属性源（不制造无意义属性源）")
    void noPropertySourceShouldBeAddedWhenNothingToInject() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("application", Map.of(
            TENANT_FAIL_ON_MISSING, "false")));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getPropertySources().contains("ypbinDownstreamDefaults")).isFalse();
        assertThat(environment.getProperty(TENANT_FAIL_ON_MISSING, Boolean.class)).isFalse();
    }

    // ------------------------------------------------------------------ 总开关

    @Test
    @DisplayName("★ 总开关关闭时不注入任何默认值")
    void disabledSwitchShouldSkipInjection() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("application", Map.of(
            ENABLED, "false")));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(TENANT_FAIL_ON_MISSING)).isNull();
        assertThat(environment.getProperty(SECURITY_INTERCEPTOR)).isNull();
    }

    @Test
    @DisplayName("总开关默认开启")
    void switchShouldDefaultToEnabled() {
        StandardEnvironment environment = new StandardEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(ENABLED, Boolean.class, true)).isTrue();
        assertThat(environment.getProperty(TENANT_FAIL_ON_MISSING)).isNotNull();
    }

    // ------------------------------------------------------------------ 凭据红线

    @Test
    @DisplayName("★★ 绝不为 trusted-source-token 注入默认值（真实凭据，默认值等同无校验）")
    void mustNotProvideTrustedSourceTokenDefault() {
        StandardEnvironment environment = new StandardEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(TRUSTED_SOURCE_TOKEN))
            .as("trusted-source-token 是真实凭据，starter 不得提供任何默认值")
            .isNull();
        MapPropertySource source = (MapPropertySource) environment.getPropertySources()
            .get("ypbinDownstreamDefaults");
        assertThat(source).isNotNull();
        assertThat(source.getPropertyNames()).doesNotContain(TRUSTED_SOURCE_TOKEN);
    }

    // ------------------------------------------------------------------ 幂等与注册

    @Test
    @DisplayName("重复调用幂等：不重复添加属性源")
    void repeatedInvocationShouldBeIdempotent() {
        StandardEnvironment environment = new StandardEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());
        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getPropertySources().contains("ypbinDownstreamDefaults")).isTrue();
        assertThat(environment.getProperty(TENANT_FAIL_ON_MISSING, Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("★ 已在 spring.factories 注册，Boot 能通过 SpringFactoriesLoader 发现")
    void shouldBeRegisteredInSpringFactories() {
        // 宽松失败处理：classpath 上还有框架/第三方注册的 EP（部分需要构造参数，
        // 如 CloudFoundryVcapEnvironmentPostProcessor 依赖 DeferredLogFactory），
        // 它们的实例化失败不应影响对本类注册结果的判断
        List<String> failures = new ArrayList<>();
        List<String> discovered = SpringFactoriesLoader.forDefaultResourceLocation()
            .load(EnvironmentPostProcessor.class,
                SpringFactoriesLoader.ArgumentResolver.none(),
                (factoryType, factoryName, failure) -> failures.add(factoryName))
            .stream()
            .map(found -> found.getClass().getName())
            .toList();

        assertThat(discovered)
            .as("注册键必须是 org.springframework.boot.EnvironmentPostProcessor，且类名与实现一致")
            .contains(DownstreamDefaultsEnvironmentPostProcessor.class.getName());
        assertThat(failures)
            .as("本类必须能无参实例化（否则注册了也装配不上）")
            .doesNotContain(DownstreamDefaultsEnvironmentPostProcessor.class.getName());
    }

    @Test
    @DisplayName("顺序为最低优先级")
    void orderShouldBeLowestPrecedence() {
        assertThat(processor.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }
}

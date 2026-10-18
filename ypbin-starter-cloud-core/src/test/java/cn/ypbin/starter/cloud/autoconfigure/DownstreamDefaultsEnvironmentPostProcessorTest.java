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
 * <p><b>四条主线索</b>：默认值确实注入、业务显式配置优先、总开关能关闭、
 * 真实凭据<b>没有</b>被给默认值。最后一条是这个需求最容易写错、后果也最严重的一条
 * （默认凭据等同于没有凭据校验），必须显式钉住。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class DownstreamDefaultsEnvironmentPostProcessorTest {

    private static final String SECURITY_INTERCEPTOR = "ypbin.security.interceptor";
    private static final String TENANT_FAIL_ON_MISSING = "ypbin.tenant.fail-on-missing-tenant";
    private static final String TRUSTED_SOURCE_TOKEN = "ypbin.cloud.feign.trusted-source-token";

    private final DownstreamDefaultsEnvironmentPostProcessor processor =
        new DownstreamDefaultsEnvironmentPostProcessor();

    // ------------------------------------------------------------------ 默认注入

    @Test
    @DisplayName("★ 默认注入下游两项：关闭登录拦截 + 缺租户上下文时失败")
    void shouldInjectDownstreamDefaults() {
        StandardEnvironment environment = new StandardEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(SECURITY_INTERCEPTOR, Boolean.class)).isFalse();
        assertThat(environment.getProperty(TENANT_FAIL_ON_MISSING, Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("★ 注入位置是最低优先级属性源（业务配置一定覆盖它）")
    void defaultsShouldBeLowestPrecedence() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("application", Map.of(
            "ypbin.security.interceptor", "true")));

        processor.postProcessEnvironment(environment, new SpringApplication());

        List<String> names = new ArrayList<>();
        environment.getPropertySources().forEach(source -> names.add(source.getName()));
        assertThat(names).last().isEqualTo("ypbinDownstreamDefaults");
        assertThat(environment.getProperty(SECURITY_INTERCEPTOR, Boolean.class)).isTrue();
    }

    // ------------------------------------------------------------------ 业务覆盖

    @Test
    @DisplayName("★ 业务显式配置优先：只覆盖一项时另一项仍用默认值（逐项判断，而非整源跳过）")
    void explicitOverrideShouldWinPerProperty() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("application", Map.of(
            SECURITY_INTERCEPTOR, "false",
            TENANT_FAIL_ON_MISSING, "false")));

        processor.postProcessEnvironment(environment, new SpringApplication());

        // 业务把 fail-on-missing-tenant 显式设为 false（如单体应用），默认值不得覆盖它
        assertThat(environment.getProperty(TENANT_FAIL_ON_MISSING, Boolean.class)).isFalse();
        assertThat(environment.getProperty(SECURITY_INTERCEPTOR, Boolean.class)).isFalse();
    }

    @Test
    @DisplayName("★ 业务只配一项时，未配的那项仍获得默认值")
    void partialOverrideShouldKeepOtherDefault() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("application", Map.of(
            TENANT_FAIL_ON_MISSING, "false")));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(TENANT_FAIL_ON_MISSING, Boolean.class)).isFalse();
        // 未配置的登录拦截仍被关闭（否则下游必然 401）
        assertThat(environment.getProperty(SECURITY_INTERCEPTOR, Boolean.class)).isFalse();
    }

    // ------------------------------------------------------------------ 总开关

    @Test
    @DisplayName("★ 总开关关闭时不注入任何默认值")
    void disabledSwitchShouldSkipInjection() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("application", Map.of(
            DownstreamDefaultsEnvironmentPostProcessor.ENABLED_PROPERTY, "false")));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(SECURITY_INTERCEPTOR)).isNull();
        assertThat(environment.getProperty(TENANT_FAIL_ON_MISSING)).isNull();
    }

    @Test
    @DisplayName("总开关默认开启（未配置时按 true 处理）")
    void switchShouldDefaultToEnabled() {
        StandardEnvironment environment = new StandardEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty(DownstreamDefaultsEnvironmentPostProcessor.ENABLED_PROPERTY,
            Boolean.class, true)).isTrue();
        assertThat(environment.getProperty(SECURITY_INTERCEPTOR)).isNotNull();
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
        // 属性源里也不应出现该键（避免后续版本「顺手加个默认」）
        MapPropertySource source = (MapPropertySource) environment.getPropertySources()
            .get("ypbinDownstreamDefaults");
        assertThat(source).isNotNull();
        assertThat(source.getPropertyNames()).doesNotContain(TRUSTED_SOURCE_TOKEN);
    }

    @Test
    @DisplayName("★ 属性源不包含除两项下游默认之外的其它键（防止默认值面无序扩大）")
    void propertySourceShouldOnlyContainDownstreamDefaults() {
        StandardEnvironment environment = new StandardEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        MapPropertySource source = (MapPropertySource) environment.getPropertySources()
            .get("ypbinDownstreamDefaults");
        assertThat(source).isNotNull();
        assertThat(source.getPropertyNames()).containsExactlyInAnyOrder(
            SECURITY_INTERCEPTOR, TENANT_FAIL_ON_MISSING);
    }

    // ------------------------------------------------------------------ 幂等与注册

    @Test
    @DisplayName("重复调用幂等：不重复添加属性源")
    void repeatedInvocationShouldBeIdempotent() {
        StandardEnvironment environment = new StandardEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());
        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getPropertySources().contains("ypbinDownstreamDefaults")).isTrue();
        assertThat(environment.getProperty(SECURITY_INTERCEPTOR, Boolean.class)).isFalse();
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
            .map(processor -> processor.getClass().getName())
            .toList();

        assertThat(discovered)
            .as("注册键必须是 org.springframework.boot.EnvironmentPostProcessor，且类名与实现一致")
            .contains(DownstreamDefaultsEnvironmentPostProcessor.class.getName());
        assertThat(failures)
            .as("本类必须能无参实例化（否则注册了也装配不上）")
            .doesNotContain(DownstreamDefaultsEnvironmentPostProcessor.class.getName());
    }

    @Test
    @DisplayName("顺序为最低优先级（其它后处理器先执行，本类的默认值不被更晚的覆盖意图打乱）")
    void orderShouldBeLowestPrecedence() {
        assertThat(processor.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }
}

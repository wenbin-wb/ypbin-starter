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

import cn.ypbin.starter.security.autoconfigure.SecurityProperties;
import cn.ypbin.starter.security.satoken.SaTokenWebConfigurer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;

/**
 * 内部凭证守卫与 Sa-Token 全局登录拦截的<b>协同</b>回归测试。
 *
 * <p>锁定 issue #91 的根因：守卫的 ENVELOPE 语义是 MVC 拦截器，而 Sa-Token 默认拦截 {@code /**}
 * 并要求登录态；服务间 Feign 直连只带 {@code X-Internal-Token}、没有用户会话，会在守卫执行<strong>之前</strong>
 * 就抛 {@code NotLoginException} ⇒ 内部凭证校验形同虚设。修复方式是让守卫把自身接管路径贡献给
 * {@link cn.ypbin.starter.security.satoken.SecurityExcludePathProvider} 放行清单。</p>
 *
 * <p>测试用真实的 {@link SaTokenWebConfigurer} 在真实 {@link InterceptorRegistry} 上完成注册，再用
 * Spring MVC 真实的 {@link MappedInterceptor#matches(jakarta.servlet.http.HttpServletRequest)} 判定
 * 路径是否放行——不复制组件内部的路径逻辑，避免假绿。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
class InternalTokenGuardSaTokenInteropTest {

    @Test
    @DisplayName("★ 守卫接管路径从 Sa-Token 登录拦截中豁免；豁免范围严格限于这些路径")
    void guardPathsAreExcludedFromSaTokenLoginCheck() {
        MappedInterceptor saToken = saTokenInterceptor(guardProperties(List.of("/internal/mqtt/*")));

        assertThat(saToken.getExcludePathPatterns())
            .as("守卫覆盖路径必须进入放行清单")
            .contains("/internal/**", "/internal/mqtt/*");
        assertThat(saToken.matches(request("/internal/data")))
            .as("内部端点不得再被 Sa-Token 登录校验拦截（安全边界已由凭证守卫接管）")
            .isFalse();
        assertThat(saToken.matches(request("/internal/mqtt/webhook")))
            .as("RAW_401 路径同样不得被登录拦截抢先拒绝（EMQX 侧只认状态码）")
            .isFalse();
        assertThat(saToken.matches(request("/api/devices")))
            .as("豁免范围不得放大到全站：其它路径仍必须有登录校验")
            .isTrue();
    }

    @Test
    @DisplayName("★ 守卫关闭时不贡献任何放行路径（纵深防御：即便配了清单也不得豁免登录拦截）")
    void noContributionWhenGuardDisabled() {
        InternalTokenProperties disabled = new InternalTokenProperties();
        disabled.setGuardEnabled(false);
        // 即便路径清单非空：守卫没开 ⇒ 没有任何凭证校验接管这些路径 ⇒ 登录拦截必须保留。
        // （此前该用例把清单也清空了，只证明「未配置路径」，并未真正覆盖「守卫关闭」。）
        disabled.setRaw401PathPatterns(List.of("/internal/mqtt/*"));

        MappedInterceptor saToken = saTokenInterceptor(disabled);

        assertThat(saToken.getExcludePathPatterns())
            .as("守卫关闭时不得贡献任何放行项（MappedInterceptor 无 excludes 时为 null）")
            .isNullOrEmpty();
        assertThat(saToken.matches(request("/internal/data")))
            .as("守卫关闭时内部端点仍必须有登录校验")
            .isTrue();
        assertThat(saToken.matches(request("/internal/mqtt/webhook")))
            .as("RAW_401 路径在守卫关闭时同样不得被豁免")
            .isTrue();
    }

    @Test
    @DisplayName("★ envelope 配置带首尾空白时：Sa-Token 豁免与守卫 include 必须剪裁一致（否则出现裸路径）")
    void envelopePatternsAreTrimmedConsistently() {
        InternalTokenProperties properties = new InternalTokenProperties();
        properties.setGuardEnabled(true);
        properties.setToken("s3cr3t");
        properties.setEnvelopePathPatterns(List.of(" /internal/** "));
        properties.setRaw401PathPatterns(List.of());

        MappedInterceptor saToken = saTokenInterceptor(properties);
        CapturingRegistry guardRegistry = new CapturingRegistry();
        new InternalTokenGuardWebMvcConfigurer(properties).addInterceptors(guardRegistry);
        MappedInterceptor guard = guardRegistry.captured().stream()
            .filter(MappedInterceptor.class::isInstance)
            .map(MappedInterceptor.class::cast)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("守卫拦截器未注册"));

        assertThat(saToken.getExcludePathPatterns()).containsExactly("/internal/**");
        assertThat(guard.matches(request("/internal/x")))
            .as("Sa-Token 已豁免的路径必须由守卫真正接管——两边都不匹配就是裸路径")
            .isTrue();
    }

    @Test
    @DisplayName("RAW_401 的 Ant 风格写法经归一化后放行（与过滤器注册使用同一结果）")
    void antStyleRaw401IsNormalizedBeforeExcluding() {
        MappedInterceptor saToken = saTokenInterceptor(guardProperties(List.of("/internal/mqtt/**")));

        assertThat(saToken.getExcludePathPatterns())
            .as("归一化后不应把 Ant 的 '**' 原样带进放行清单（否则放行宽于守卫覆盖）")
            .contains("/internal/**", "/internal/mqtt/*")
            .doesNotContain("/internal/mqtt/**");
    }

    private static InternalTokenProperties guardProperties(List<String> raw401Patterns) {
        InternalTokenProperties properties = new InternalTokenProperties();
        properties.setGuardEnabled(true);
        properties.setToken("s3cr3t");
        properties.setRaw401PathPatterns(raw401Patterns);
        return properties;
    }

    /** 用真实的 {@link SaTokenWebConfigurer} 注册拦截器并取回 MVC 的路径匹配结果。 */
    private static MappedInterceptor saTokenInterceptor(InternalTokenProperties guardProperties) {
        SecurityProperties security = new SecurityProperties();
        CapturingRegistry registry = new CapturingRegistry();
        new SaTokenWebConfigurer(security, List.of(new InternalTokenGuardExcludePathProvider(guardProperties)))
            .addInterceptors(registry);
        return registry.captured().stream()
            .filter(MappedInterceptor.class::isInstance)
            .map(MappedInterceptor.class::cast)
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Sa-Token 拦截器未注册，测试前置条件不成立"));
    }

    private static MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        request.setServletPath(path);
        // MappedInterceptor#matches 需要预先解析的 RequestPath（真实请求分派时由 DispatcherServlet 完成）
        ServletRequestPathUtils.parseAndCache(request);
        return request;
    }

    /** 暴露 {@link InterceptorRegistry#getInterceptors()} 的测试子类。 */
    static final class CapturingRegistry extends InterceptorRegistry {

        List<Object> captured() {
            return new ArrayList<>(getInterceptors());
        }
    }
}

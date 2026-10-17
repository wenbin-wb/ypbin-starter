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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

/**
 * {@link InternalTokenGuardWebMvcConfigurer} 的路径范围测试。
 *
 * <p>锁定「生效路径可配置（默认 {@code /internal/**}）」与「空清单不注册」两条：后者是避免
 * {@code addPathPatterns()} 空数组语义含糊导致误拦全站。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
class InternalTokenGuardWebMvcConfigurerTest {

    private static InternalTokenProperties properties(List<String> patterns) {
        InternalTokenProperties properties = new InternalTokenProperties();
        properties.setGuardEnabled(true);
        properties.setToken("s3cr3t");
        properties.setEnvelopePathPatterns(patterns);
        return properties;
    }

    /**
     * 捕获注册到 registry 的拦截器与其路径。
     *
     * <p>注意：{@code InterceptorRegistration} 同时提供 {@code addPathPatterns(String...)} 与
     * {@code addPathPatterns(List<String>)} 两个重载，生产代码传 {@code List} ⇒ 必须按
     * {@code List} 重载打桩与捕获，否则捕获不到实际调用（Mockito 按方法签名区分重载）。</p>
     */
    @SuppressWarnings("unchecked")
    private static InterceptorRegistration captureRegistration(InternalTokenProperties properties) {
        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        InterceptorRegistration registration = mock(InterceptorRegistration.class);
        when(registry.addInterceptor(any(HandlerInterceptor.class))).thenReturn(registration);
        when(registration.addPathPatterns(anyList())).thenReturn(registration);

        new InternalTokenGuardWebMvcConfigurer(properties).addInterceptors(registry);

        ArgumentCaptor<List<String>> patterns = ArgumentCaptor.forClass(List.class);
        verify(registration).addPathPatterns(patterns.capture());
        assertThat(patterns.getValue()).containsExactlyElementsOf(
            properties.getEnvelopePathPatterns().stream().filter(p -> !p.isBlank()).toList());
        return registration;
    }

    @Test
    @DisplayName("默认生效路径为 /internal/**（与既有业务实现一致）")
    void defaultPatternIsInternalWildcard() {
        assertThat(new InternalTokenProperties().getEnvelopePathPatterns())
            .containsExactly("/internal/**");
    }

    @Test
    @DisplayName("注册的拦截器是 ENVELOPE 语义的守卫，路径取自配置")
    void registersInterceptorOnConfiguredPatterns() {
        captureRegistration(properties(List.of("/internal/**", "/act-internal/**")));
    }

    @Test
    @DisplayName("路径清单为空（含全空白项）时不注册任何拦截器")
    void emptyPatternsRegisterNothing() {
        for (List<String> patterns : List.<List<String>>of(List.of(), List.of("   ", ""))) {
            InterceptorRegistry registry = mock(InterceptorRegistry.class);
            new InternalTokenGuardWebMvcConfigurer(properties(patterns)).addInterceptors(registry);
            verify(registry, never()).addInterceptor(any(HandlerInterceptor.class));
        }
    }

    @Test
    @DisplayName("路径清单为 null 时按空处理，不抛异常（配置绑定容错）")
    void nullPatternsRegisterNothing() {
        InternalTokenProperties properties = properties(List.of());
        properties.setEnvelopePathPatterns(null);
        assertThat(properties.getEnvelopePathPatterns()).isEmpty();

        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        new InternalTokenGuardWebMvcConfigurer(properties).addInterceptors(registry);
        verify(registry, never()).addInterceptor(any(HandlerInterceptor.class));
    }
}

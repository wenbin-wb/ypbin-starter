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
package cn.ypbin.starter.tools.support;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.core.blocklist.ClientIpResolver;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 请求上下文工具测试。
 *
 * <p>客户端 IP 部分额外覆盖 {@code ypbin.tools.trusted-proxies}：配置后必须与 core/sign/log 的
 * {@link ClientIpResolver} 口径一致，未配置时保持「最右一跳」旧语义。</p>
 *
 * @author wenbin
 * @since 2026-08-31
 */
class RequestUtilsTest {

    private static final List<String> TRUSTED = List.of("10.0.0.0/8");

    @AfterEach
    void tearDown() {
        RequestUtils.configureTrustedProxies(null);
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldReturnNullWithoutContext() {
        assertThat(RequestUtils.getRequest()).isNull();
        assertThat(RequestUtils.getClientIp()).isEqualTo("unknown");
    }

    @Test
    void shouldReadRequestFromContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 5.6.7.8");
        request.addHeader("User-Agent", "test-agent");
        request.addHeader("X-Custom", "custom-value");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(RequestUtils.getRequest()).isSameAs(request);
        // 默认不信任转发头：客户端可自行伪造 XFF，不能作为来源
        assertThat(RequestUtils.getClientIp()).isEqualTo("10.0.0.1");
        assertThat(RequestUtils.getUserAgent()).isEqualTo("test-agent");
        assertThat(RequestUtils.getHeader("X-Custom")).isEqualTo("custom-value");
        assertThat(RequestUtils.getHeaders()).containsKey("x-custom");
    }

    @Test
    void shouldFallbackToRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(RequestUtils.getClientIp()).isEqualTo("10.0.0.9");
    }

    @Test
    void shouldUseRemoteAddrWhenNotTrustingForwarded() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 5.6.7.8");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        // 不信任转发头时取真实对端地址，防伪造 XFF 绕过限流
        assertThat(RequestUtils.getClientIp(false)).isEqualTo("10.0.0.1");
        // 显式信任转发头时取最右一跳（代理追加的真实来源），而不是客户端可控的最左段
        assertThat(RequestUtils.getClientIp(true)).isEqualTo("5.6.7.8");
    }

    @Test
    void trustedProxiesShouldResolveLikeCoreResolver() {
        RequestUtils.configureTrustedProxies(TRUSTED);
        // 客户端 → nginx(10.0.0.7) → 应用直连方(10.0.0.5)
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader(ClientIpResolver.HEADER_FORWARDED_FOR, "203.0.113.9, 10.0.0.7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        String resolved = RequestUtils.getClientIp(true);

        assertThat(resolved).isEqualTo("203.0.113.9");
        // 与 core 的解析器逐字一致：sign/log/core 用的就是它
        assertThat(new ClientIpResolver(TRUSTED).resolve("10.0.0.5", "203.0.113.9, 10.0.0.7", null))
            .isEqualTo(resolved);
    }

    @Test
    void untrustedDirectPeerShouldIgnoreForwardedHeaders() {
        RequestUtils.configureTrustedProxies(TRUSTED);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.5");
        request.addHeader(ClientIpResolver.HEADER_FORWARDED_FOR, "203.0.113.9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertThat(RequestUtils.getClientIp(true)).isEqualTo("198.51.100.5");
    }
}

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
package cn.ypbin.starter.tracking.web;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.tracking.core.TrackRequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 采集上下文解析测试：取值来自服务端请求，且在没有请求上下文时不报错。
 *
 * @author wenbin
 * @since 2026-09-15
 */
class TrackRequestContextResolverTest {

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static void bindRequest(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void shouldResolveFromCurrentRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.2.3");
        request.addHeader("User-Agent", "JUnit-Agent/1.0");
        request.addHeader("X-Request-Id", "trace-123");
        bindRequest(request);

        TrackRequestContext context = new TrackRequestContextResolver(false).resolve();

        assertThat(context.clientIp()).isEqualTo("10.1.2.3");
        assertThat(context.userAgent()).isEqualTo("JUnit-Agent/1.0");
        assertThat(context.traceId()).isEqualTo("trace-123");
    }

    @Test
    void shouldHonorTrustForwardedSwitch() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.16.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 172.16.0.1");
        bindRequest(request);

        // 网关之后：信任转发头，取到真实客户端
        assertThat(new TrackRequestContextResolver(true).resolve().clientIp()).isEqualTo("203.0.113.9");
        // 未开启：只取对端地址（即网关本身），这是刻意的安全默认值
        assertThat(new TrackRequestContextResolver(false).resolve().clientIp()).isEqualTo("172.16.0.1");
    }

    @Test
    void shouldTolerateMissingRequestContext() {
        TrackRequestContext context = new TrackRequestContextResolver(false).resolve();

        // RequestUtils 取不到值时会返回占位串，解析器负责归一为 null，而不是把 "unknown" 当 IP 存下来
        assertThat(context.clientIp()).isNull();
        assertThat(context.userAgent()).isNull();
        // 链路 ID 缺失时生成一个，避免整列空值
        assertThat(context.traceId()).isNotBlank();
    }
}

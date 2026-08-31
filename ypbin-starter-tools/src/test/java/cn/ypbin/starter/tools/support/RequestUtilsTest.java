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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 请求上下文工具测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class RequestUtilsTest {

    @AfterEach
    void tearDown() {
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
        assertThat(RequestUtils.getClientIp()).isEqualTo("1.2.3.4");
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
}

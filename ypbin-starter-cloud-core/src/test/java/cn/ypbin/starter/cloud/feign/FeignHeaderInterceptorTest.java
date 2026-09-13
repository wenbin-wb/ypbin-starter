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
package cn.ypbin.starter.cloud.feign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import feign.RequestTemplate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@link FeignHeaderInterceptor} 单元测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class FeignHeaderInterceptorTest {

    /** 兼容构造：不启用来源校验（等价旧行为） */
    private static FeignHeaderInterceptor noSourceCheck(List<String> headers) {
        return new FeignHeaderInterceptor(headers, List.of(), "X-Gateway-Signed", "");
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldPropagateConfiguredHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "req-1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        FeignHeaderInterceptor interceptor = noSourceCheck(List.of("X-Request-Id"));
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get("X-Request-Id")).containsExactly("req-1");
    }

    @Test
    void shouldNotDuplicateHeaderByCaseInsensitiveName() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "from-request");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        FeignHeaderInterceptor interceptor = noSourceCheck(List.of("X-Request-Id"));
        RequestTemplate template = new RequestTemplate();
        template.header("x-request-id", "existing");

        interceptor.apply(template);

        assertThat(template.headers()).containsOnlyKeys("x-request-id");
        assertThat(template.headers().get("x-request-id")).containsExactly("existing");
    }

    @Test
    void shouldSkipWhenRequestContextMissing() {
        FeignHeaderInterceptor interceptor = noSourceCheck(List.of("X-Request-Id"));
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers()).isEmpty();
    }

    @Test
    void shouldDropIdentityHeadersWhenSourceUntrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "1");
        request.addHeader("X-Request-Id", "req-1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        FeignHeaderInterceptor interceptor = new FeignHeaderInterceptor(
            List.of("X-Request-Id", "X-User-Id"), List.of("X-User-Id"),
            "X-Gateway-Signed", "s3cret");
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        // 非身份头照常透传，身份头因来源不可信被丢弃
        assertThat(template.headers().get("X-Request-Id")).containsExactly("req-1");
        assertThat(template.headers()).doesNotContainKey("X-User-Id");
    }

    @Test
    void shouldFailFastWhenTrustedSourceRequiredButNotConfigured() {
        assertThatThrownBy(() -> new FeignHeaderInterceptor(
            List.of("X-User-Id"), List.of("X-User-Id"), "X-Gateway-Signed", "", true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("require-trusted-source");
    }

    @Test
    void shouldStartWhenTrustedSourceRequiredAndConfigured() {
        assertThat(new FeignHeaderInterceptor(
            List.of("X-User-Id"), List.of("X-User-Id"), "X-Gateway-Signed", "s3cret", true))
            .isNotNull();
    }

    @Test
    void shouldPropagateIdentityHeadersWhenSourceTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "1");
        request.addHeader("X-Gateway-Signed", "s3cret");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        FeignHeaderInterceptor interceptor = new FeignHeaderInterceptor(
            List.of("X-User-Id"), List.of("X-User-Id"), "X-Gateway-Signed", "s3cret");
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get("X-User-Id")).containsExactly("1");
    }
}

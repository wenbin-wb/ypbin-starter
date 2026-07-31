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

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldPropagateConfiguredHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "req-1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        FeignHeaderInterceptor interceptor = new FeignHeaderInterceptor(List.of("X-Request-Id"));
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get("X-Request-Id")).containsExactly("req-1");
    }

    @Test
    void shouldNotDuplicateHeaderByCaseInsensitiveName() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "from-request");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        FeignHeaderInterceptor interceptor = new FeignHeaderInterceptor(List.of("X-Request-Id"));
        RequestTemplate template = new RequestTemplate();
        template.header("x-request-id", "existing");

        interceptor.apply(template);

        assertThat(template.headers()).containsOnlyKeys("x-request-id");
        assertThat(template.headers().get("x-request-id")).containsExactly("existing");
    }

    @Test
    void shouldSkipWhenRequestContextMissing() {
        FeignHeaderInterceptor interceptor = new FeignHeaderInterceptor(List.of("X-Request-Id"));
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers()).isEmpty();
    }
}

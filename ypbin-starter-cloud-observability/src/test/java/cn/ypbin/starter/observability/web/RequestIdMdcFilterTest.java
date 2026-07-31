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
package cn.ypbin.starter.observability.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link RequestIdMdcFilter} 单元测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class RequestIdMdcFilterTest {

    @Test
    void shouldReuseRequestIdFromHeaderAndWriteMdc() throws Exception {
        RequestIdMdcFilter filter = new RequestIdMdcFilter("X-Request-Id", "requestId");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "req-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] captured = new String[1];
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
                throws java.io.IOException, jakarta.servlet.ServletException {
                captured[0] = MDC.get("requestId");
                super.doFilter(req, res);
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(captured[0]).isEqualTo("req-123");
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("req-123");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void shouldGenerateRequestIdWhenHeaderMissing() throws Exception {
        RequestIdMdcFilter filter = new RequestIdMdcFilter("X-Request-Id", "requestId");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Request-Id")).isNotBlank();
    }

    @Test
    void shouldClearMdcAfterRequest() throws Exception {
        RequestIdMdcFilter filter = new RequestIdMdcFilter("X-Request-Id", "requestId");

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(MDC.get("requestId")).isNull();
    }
}

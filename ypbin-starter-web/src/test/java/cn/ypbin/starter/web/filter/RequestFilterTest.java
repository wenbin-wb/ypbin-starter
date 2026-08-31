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
package cn.ypbin.starter.web.filter;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.web.request.RepeatableReadRequestFilter;
import cn.ypbin.starter.web.request.RepeatableReadRequestWrapper;
import cn.ypbin.starter.web.xss.XssFilter;
import cn.ypbin.starter.web.xss.XssHttpServletRequestWrapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 请求过滤器链测试：重复读包装与 XSS 清洗的包装/放行逻辑。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class RequestFilterTest {

    @Test
    void repeatableReadShouldWrapJsonRequest() throws Exception {
        RepeatableReadRequestFilter filter = new RepeatableReadRequestFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api");
        request.setContentType("application/json");
        request.setContent("{\"a\":1}".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isInstanceOf(RepeatableReadRequestWrapper.class);
    }

    @Test
    void repeatableReadShouldSkipMultipart() throws Exception {
        RepeatableReadRequestFilter filter = new RepeatableReadRequestFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api");
        request.setContentType("multipart/form-data; boundary=x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void repeatableReadShouldSkipNullContentType() throws Exception {
        RepeatableReadRequestFilter filter = new RepeatableReadRequestFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void xssFilterShouldWrapNormalRequest() throws Exception {
        XssFilter filter = new XssFilter(List.of());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api");
        request.setParameter("q", "<script>alert(1)</script>");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isInstanceOf(XssHttpServletRequestWrapper.class);
        String cleaned = ((XssHttpServletRequestWrapper) chain.getRequest()).getParameter("q");
        assertThat(cleaned).doesNotContain("<script>");
    }

    @Test
    void xssFilterShouldSkipExcludedPath() throws Exception {
        XssFilter filter = new XssFilter(List.of("/excluded/**"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/excluded/a");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void xssFilterShouldWrapNonExcludedPath() throws Exception {
        XssFilter filter = new XssFilter(List.of("/excluded/**"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/normal");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isInstanceOf(XssHttpServletRequestWrapper.class);
    }
}

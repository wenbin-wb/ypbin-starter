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
package cn.ypbin.starter.web.request;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.web.xss.XssHttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 请求包装类测试：重复读缓存与 XSS 参数清洗。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class RequestWrapperTest {

    @Test
    void repeatableReadShouldCacheBodyAndAllowMultipleReads() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api");
        request.setContentType("application/json");
        request.setContent("{\"key\":\"value\"}".getBytes());

        RepeatableReadRequestWrapper wrapper = new RepeatableReadRequestWrapper(request);
        assertThat(wrapper.getBodyAsString()).isEqualTo("{\"key\":\"value\"}");
        assertThat(wrapper.getBodyAsString()).isEqualTo("{\"key\":\"value\"}");
        assertThat(wrapper.getBodyAsBytes()).hasSize(15);

        // 输入流可重复读
        BufferedReader reader = new BufferedReader(new InputStreamReader(wrapper.getInputStream()));
        assertThat(reader.readLine()).isEqualTo("{\"key\":\"value\"}");
        assertThat(wrapper.getInputStream().isFinished()).isFalse();
        assertThat(wrapper.getInputStream().isReady()).isTrue();
    }

    @Test
    void xssWrapperShouldCleanParametersAndHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api");
        request.addParameter("q", "<script>alert(1)</script>");
        request.addParameter("tags", new String[] {"<script>alert(1)</script>", "ok"});
        request.addHeader("X-Forwarded-For", "1.1.1.1 <script>alert(1)</script>");

        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(request);
        assertThat(wrapper.getParameter("q")).doesNotContain("<script>");
        assertThat(wrapper.getParameterValues("tags")[0]).doesNotContain("<script>");
        assertThat(wrapper.getParameterValues("tags")[1]).isEqualTo("ok");
        assertThat(wrapper.getHeader("X-Forwarded-For")).doesNotContain("<script>");
        assertThat(wrapper.getParameterMap()).containsKey("q");
    }

    @Test
    void repeatableReadShouldHandleEmptyBody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api");
        request.setContentType("application/json");
        request.setContent(new byte[0]);

        RepeatableReadRequestWrapper wrapper = new RepeatableReadRequestWrapper(request);
        assertThat(wrapper.getBodyAsString()).isEmpty();
    }

    @Test
    void xssWrapperShouldPreserveCleanValues() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api");
        request.addParameter("name", "张三");
        XssHttpServletRequestWrapper wrapper = new XssHttpServletRequestWrapper(request);
        assertThat(wrapper.getParameter("name")).isEqualTo("张三");
    }
}

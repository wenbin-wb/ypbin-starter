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
package cn.ypbin.starter.sign.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.ypbin.starter.sign.autoconfigure.SignProperties;
import cn.ypbin.starter.sign.core.SignChecker;
import cn.ypbin.starter.sign.core.SignFailure;
import cn.ypbin.starter.sign.core.SignResult;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link SignInterceptor} 失败响应契约。
 *
 * <p>此前所有失败都经 {@code R.fail(message)} 返回 code=500，前端会当成服务端故障重试；
 * 本类断言失败类型映射到 401 / 403 / 400，且 HTTP 状态码仍为 200（业务响应一律 200）。</p>
 *
 * @author wenbin
 * @since 2026-11-06
 */
class SignInterceptorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void unauthorizedFailureShouldReturnCode401() throws Exception {
        MockHttpServletResponse response = invoke(SignResult.fail("缺少签名参数", SignFailure.UNAUTHORIZED));

        assertThat(bodyCode(response)).isEqualTo(401);
    }

    @Test
    void forbiddenFailureShouldReturnCode403() throws Exception {
        MockHttpServletResponse response = invoke(SignResult.fail("签名验证失败", SignFailure.FORBIDDEN));

        assertThat(bodyCode(response)).isEqualTo(403);
    }

    @Test
    void badRequestFailureShouldReturnCode400() throws Exception {
        MockHttpServletResponse response = invoke(SignResult.fail("时间戳格式错误", SignFailure.BAD_REQUEST));

        assertThat(bodyCode(response)).isEqualTo(400);
    }

    /** 自定义校验器只用旧的 {@code fail(message)}：按 403 呈现，不得退回 500。 */
    @Test
    void failureWithoutTypeShouldFallBackTo403() throws Exception {
        MockHttpServletResponse response = invoke(SignResult.fail("自定义原因"));

        assertThat(bodyCode(response)).isEqualTo(403);
    }

    @Test
    void successfulCheckShouldPassThrough() throws Exception {
        SignInterceptor interceptor = interceptorFor(SignResult.ok("app-1"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean handled = interceptor.preHandle(
            new MockHttpServletRequest("GET", "/api/order"), response, new Object());

        assertThat(handled).isTrue();
        assertThat(response.getContentAsString()).isEmpty();
    }

    private static MockHttpServletResponse invoke(SignResult result) throws Exception {
        SignInterceptor interceptor = interceptorFor(result);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean handled = interceptor.preHandle(
            new MockHttpServletRequest("GET", "/api/order"), response, new Object());
        assertThat(handled).isFalse();
        // 契约：业务响应一律 HTTP 200，失败由 R.code 表达
        assertThat(response.getStatus()).isEqualTo(200);
        return response;
    }

    private static SignInterceptor interceptorFor(SignResult result) {
        SignChecker checker = mock(SignChecker.class);
        when(checker.check(any())).thenReturn(result);
        SignProperties properties = new SignProperties();
        properties.setMode(SignProperties.Mode.GLOBAL);
        return new SignInterceptor(properties, checker, MAPPER);
    }

    private static Object bodyCode(MockHttpServletResponse response) throws Exception {
        Map<?, ?> body = MAPPER.readValue(response.getContentAsString(), Map.class);
        assertThat(body.get("success")).isEqualTo(false);
        return body.get("code");
    }
}

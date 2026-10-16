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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 两种拒绝语义的行为测试：ENVELOPE（拦截器抛业务异常）与 RAW_401（过滤器写原始 401）。
 *
 * <p>这两条语义必须<b>同时存在</b>且不得互相污染：信封是浏览器/网关接口的既有约定，原始 401 是
 * EMQX 这类"只看状态码"的入站对端所必需（HTTP 200 会被判投递成功 ⇒ 静默丢数据）。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
class InternalTokenGuardSemanticsTest {

    private static final String SECRET = "s3cr3t-internal-token";
    private static final String HEADER = "X-Internal-Token";

    private static MockHttpServletRequest request(String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        if (token != null) {
            request.addHeader(HEADER, token);
        }
        return request;
    }

    // ---------- ENVELOPE 语义 ----------

    @Test
    @DisplayName("ENVELOPE：凭证正确放行（返回 true，且不写响应）")
    void envelopePassesOnMatch() throws IOException {
        InternalTokenGuardInterceptor interceptor = new InternalTokenGuardInterceptor(SECRET, HEADER);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(request("/internal/devices", SECRET), response, new Object()))
            .isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("ENVELOPE：凭证不符抛 UNAUTHORIZED 业务异常，由全局处理器转 200 + R.code=401")
    void envelopeThrowsOnMismatch() throws IOException {
        InternalTokenGuardInterceptor interceptor = new InternalTokenGuardInterceptor(SECRET, HEADER);

        assertThatThrownBy(() -> interceptor.preHandle(
            request("/internal/devices", "wrong-value"), new MockHttpServletResponse(), new Object()))
            .isInstanceOf(BusinessException.class)
            .satisfies(thrown -> assertThat(((BusinessException) thrown).getCode())
                .isEqualTo(GlobalErrorCode.UNAUTHORIZED.getCode()))
            // 异常 message 不得包含呈递值
            .hasMessage("内部调用凭证校验失败")
            .hasMessageNotContaining("wrong-value");
    }

    @Test
    @DisplayName("ENVELOPE：凭证未配置时抛 UNAUTHORIZED（fail-closed），message 指向配置键")
    void envelopeThrowsWhenTokenUnconfigured() {
        InternalTokenGuardInterceptor interceptor = new InternalTokenGuardInterceptor(null, HEADER);

        assertThatThrownBy(() -> interceptor.preHandle(
            request("/internal/devices", SECRET), new MockHttpServletResponse(), new Object()))
            .isInstanceOf(BusinessException.class)
            .satisfies(thrown -> assertThat(((BusinessException) thrown).getCode())
                .isEqualTo(GlobalErrorCode.UNAUTHORIZED.getCode()))
            .hasMessageContaining(InternalTokenProperties.PREFIX + ".token")
            .hasMessageNotContaining(SECRET);
    }

    // ---------- RAW_401 语义 ----------

    @Test
    @DisplayName("RAW_401：凭证正确放行，调用后续过滤链")
    void raw401PassesOnMatch() throws ServletException, IOException {
        InternalTokenGuard guard = new InternalTokenGuard(SECRET, HEADER);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        guard.doFilter(request("/internal/mqtt/event", SECRET), new MockHttpServletResponse(),
            (req, res) -> chainInvoked.set(true));

        assertThat(chainInvoked).isTrue();
    }

    @Test
    @DisplayName("RAW_401：拒绝时状态码恒为原始 401（绝不是 200），且不进入过滤链")
    void raw401RejectsWithStatusCode401() throws ServletException, IOException {
        InternalTokenGuard guard = new InternalTokenGuard(SECRET, HEADER);

        for (String presented : new String[] {"wrong-value", null}) {
            AtomicBoolean chainInvoked = new AtomicBoolean(false);
            MockHttpServletResponse response = new MockHttpServletResponse();

            guard.doFilter(request("/internal/mqtt/event", presented), response,
                (req, res) -> chainInvoked.set(true));

            assertThat(chainInvoked).as("拒绝时不得放行（呈递=%s）", presented).isFalse();
            // 核心：对端只看状态码，HTTP 200 会被 EMQX 判为投递成功 ⇒ 静默丢数据
            assertThat(response.getStatus()).isEqualTo(401);
        }
    }

    @Test
    @DisplayName("RAW_401：凭证未配置时同样是原始 401（fail-closed，不退化成 200）")
    void raw401RejectsUnconfiguredWithStatus401() throws ServletException, IOException {
        InternalTokenGuard guard = new InternalTokenGuard("", HEADER);
        MockHttpServletResponse response = new MockHttpServletResponse();

        guard.doFilter(request("/internal/mqtt/event", SECRET), response, (req, res) -> { });

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("RAW_401：响应体为固定文案，不含任何输入值")
    void raw401BodyCarriesNoInput() throws ServletException, IOException {
        InternalTokenGuard guard = new InternalTokenGuard(SECRET, HEADER);
        String malicious = "attacker-supplied-\"><script>alert(1)</script>";
        MockHttpServletResponse response = new MockHttpServletResponse();

        guard.doFilter(request("/internal/mqtt/event", malicious), response, (req, res) -> { });

        String body = response.getContentAsString();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body).contains("UNAUTHORIZED");
        // 安全不变量：响应体不得回显任何输入值（防注入 / 防凭证泄露）
        assertThat(body).doesNotContain("attacker-supplied");
        assertThat(body).doesNotContain(SECRET);
        assertThat(body).doesNotContain("<script>");
    }

    @Test
    @DisplayName("两种语义对同一请求的判定一致：正确放行、错误拒绝（仅拒绝形态不同）")
    void bothModesAgreeOnTheVerdict() throws ServletException, IOException {
        InternalTokenGuardInterceptor envelope = new InternalTokenGuardInterceptor(SECRET, HEADER);
        InternalTokenGuard raw401 = new InternalTokenGuard(SECRET, HEADER);

        // 正确凭证：两种语义都放行
        assertThat(envelope.preHandle(request("/internal/x", SECRET), new MockHttpServletResponse(),
            new Object())).isTrue();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        raw401.doFilter(request("/internal/x", SECRET), new MockHttpServletResponse(),
            (req, res) -> chainInvoked.set(true));
        assertThat(chainInvoked).isTrue();

        // 错误凭证：ENVELOPE 抛异常，RAW_401 写 401——都不放行
        assertThatThrownBy(() -> envelope.preHandle(request("/internal/x", "nope"),
            new MockHttpServletResponse(), new Object())).isInstanceOf(BusinessException.class);
        MockHttpServletResponse rawResponse = new MockHttpServletResponse();
        raw401.doFilter(request("/internal/x", "nope"), rawResponse, (req, res) -> { });
        assertThat(rawResponse.getStatus()).isEqualTo(401);
    }
}

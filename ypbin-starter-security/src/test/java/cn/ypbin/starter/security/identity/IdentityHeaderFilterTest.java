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
package cn.ypbin.starter.security.identity;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.security.core.LoginUser;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link IdentityHeaderFilter} 单元测试。
 *
 * <p>覆盖 SF-5 来源校验：签名匹配才建立身份；签名缺失/不匹配一律拒绝（fail-closed），
 * 不信任任何来源的无参构造对携带身份头的请求同样拒绝。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
class IdentityHeaderFilterTest {

    private static final String HEADER = IdentityHeaders.GATEWAY_SIGNED;
    private static final String TOKEN = "s3cret-gateway-token";

    @AfterEach
    void tearDown() {
        IdentityContext.clear();
    }

    private IdentityHeaderFilter filter() {
        return new IdentityHeaderFilter(HEADER, TOKEN, null);
    }

    @Test
    void shouldBuildLoginUserFromTrustedHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IdentityHeaders.USER_ID, "42");
        request.addHeader(IdentityHeaders.USER_NAME, "bob");
        request.addHeader(IdentityHeaders.TENANT_ID, "3");
        request.addHeader(IdentityHeaders.DEPT_ID, "7");
        request.addHeader(IdentityHeaders.ROLES, "admin, user");
        request.addHeader(HEADER, TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, (req, res) -> {
            LoginUser user = IdentityContext.getLoginUser().orElseThrow();
            assertThat(user.getId()).isEqualTo(42L);
            assertThat(user.getUsername()).isEqualTo("bob");
            assertThat(user.getTenantId()).isEqualTo(3L);
            assertThat(user.getDeptId()).isEqualTo(7L);
            assertThat(user.getRoles()).containsExactlyInAnyOrder("admin", "user");
        });

        // filter 返回后上下文已清理
        assertThat(IdentityContext.isLogin()).isFalse();
    }

    @Test
    @DisplayName("签名值首尾空白可容忍：trim 后与期望值一致仍放行")
    void acceptsSignatureWithSurroundingWhitespace() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IdentityHeaders.USER_ID, "42");
        request.addHeader(HEADER, "  " + TOKEN + "  ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, (req, res) ->
            assertThat(IdentityContext.getUserId()).contains(42L));
    }

    @Test
    @DisplayName("SF-5：携带身份头但缺少来源标记 → 拒绝（fail-closed），不进入过滤链")
    void rejectsWhenSignatureMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IdentityHeaders.USER_ID, "42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter().doFilter(request, response, (req, res) -> chainInvoked.set(true));

        assertThat(chainInvoked).as("签名缺失时不得放行到业务链").isFalse();
        assertThat(IdentityContext.isLogin()).isFalse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("\"code\":403", "\"success\":false");
    }

    @Test
    @DisplayName("SF-5：携带身份头但来源标记不匹配 → 拒绝")
    void rejectsWhenSignatureMismatch() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IdentityHeaders.USER_ID, "42");
        request.addHeader(HEADER, "attacker-guessed-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        filter().doFilter(request, response, (req, res) -> chainInvoked.set(true));

        assertThat(chainInvoked).isFalse();
        assertThat(IdentityContext.isLogin()).isFalse();
        assertThat(response.getContentAsString()).contains("\"code\":403");
    }

    @Test
    @DisplayName("SF-5：无参构造（未配置来源标记）对携带身份头的请求一律拒绝——安全兜底")
    void noArgConstructorRejectsAnyIdentityHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IdentityHeaders.USER_ID, "42");
        request.addHeader(HEADER, TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);

        new IdentityHeaderFilter().doFilter(request, response, (req, res) -> chainInvoked.set(true));

        assertThat(chainInvoked).isFalse();
        assertThat(IdentityContext.isLogin()).isFalse();
        assertThat(response.getContentAsString()).contains("\"code\":403");
    }

    @Test
    void shouldSkipWhenNoUserIdHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IdentityHeaders.USER_NAME, "bob");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter().doFilter(request, response, (req, res) -> {
            // 无 X-User-Id 时不写上下文（也不要求签名：匿名请求本就没有身份头）
            assertThat(IdentityContext.isLogin()).isFalse();
        });

        assertThat(IdentityContext.isLogin()).isFalse();
    }

    @Test
    void shouldClearContextAfterChain() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IdentityHeaders.USER_ID, "1");
        request.addHeader(HEADER, TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        IdentityHeaderFilter filter = filter();
        filter.doFilter(request, response, (req, res) -> {
            // 链中上下文可见
            assertThat(IdentityContext.getUserId()).contains(1L);
        });

        assertThat(IdentityContext.isLogin()).isFalse();
    }
}

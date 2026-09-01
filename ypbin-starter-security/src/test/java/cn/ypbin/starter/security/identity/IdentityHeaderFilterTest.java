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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link IdentityHeaderFilter} 单元测试。
 *
 * @author wenbin
 * @since 2026-09-01
 */
class IdentityHeaderFilterTest {

    @AfterEach
    void tearDown() {
        IdentityContext.clear();
    }

    @Test
    void shouldBuildLoginUserFromTrustedHeaders() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IdentityHeaders.USER_ID, "42");
        request.addHeader(IdentityHeaders.USER_NAME, "bob");
        request.addHeader(IdentityHeaders.TENANT_ID, "3");
        request.addHeader(IdentityHeaders.DEPT_ID, "7");
        request.addHeader(IdentityHeaders.ROLES, "admin, user");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new IdentityHeaderFilter().doFilter(request, response, (req, res) -> {
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
    void shouldSkipWhenNoUserIdHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IdentityHeaders.USER_NAME, "bob");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new IdentityHeaderFilter().doFilter(request, response, (req, res) -> {
            // 无 X-User-Id 时不写上下文
            assertThat(IdentityContext.isLogin()).isFalse();
        });

        assertThat(IdentityContext.isLogin()).isFalse();
    }

    @Test
    void shouldClearContextAfterChain() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IdentityHeaders.USER_ID, "1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        IdentityHeaderFilter filter = new IdentityHeaderFilter();
        filter.doFilter(request, response, (req, res) -> {
            // 链中上下文可见
            assertThat(IdentityContext.getUserId()).contains(1L);
        });

        assertThat(IdentityContext.isLogin()).isFalse();
    }
}

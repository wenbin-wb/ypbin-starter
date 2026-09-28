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
package cn.ypbin.starter.security.management;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.core.PermissionProvider;
import cn.ypbin.starter.security.identity.IdentityContext;
import cn.ypbin.starter.security.satoken.StpPermissionAdapter;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link ManagementEndpointGuard} 单元测试——管理端点权限收口（反馈 UP-5）验收。
 *
 * <p>覆盖：health/info 公开端点放行；有目标权限码放行；无权限/未登录拒绝（fail-closed）；
 * 未配置 required-permission 时一律拒绝；平台超管约定放行；仅作用在 management base-path 下。</p>
 *
 * @author wenbin
 * @since 2026-09-28
 */
class ManagementEndpointGuardTest {

    private static final String REQUIRED = "system:monitor:view";

    @AfterEach
    void tearDown() {
        IdentityContext.clear();
    }

    private ManagementEndpointProperties props(String requiredPermission) {
        ManagementEndpointProperties properties = new ManagementEndpointProperties();
        properties.setGuardEnabled(true);
        properties.setRequiredPermission(requiredPermission);
        return properties;
    }

    private ManagementEndpointGuard guard(String requiredPermission) {
        return new ManagementEndpointGuard(props(requiredPermission), new StubPermissionProvider(), null);
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        request.setContextPath("");
        return request;
    }

    @Test
    @DisplayName("health/info 公开端点放行（不得误伤可用性探针）")
    void healthAndInfoArePublic() throws ServletException, IOException {
        for (String path : new String[] {"/actuator/health", "/actuator/info"}) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean chainInvoked = new AtomicBoolean(false);
            guard(REQUIRED).doFilter(request(path), response, (req, res) -> chainInvoked.set(true));

            assertThat(chainInvoked).as("%s 应放行", path).isTrue();
            assertThat(response.getContentAsString()).isEmpty();
        }
    }

    @Test
    @DisplayName("非管理路径放行：守卫只关心 management base-path 下的端点")
    void nonManagementPathPasses() throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        guard(REQUIRED).doFilter(request("/api/system/users"), response, (req, res) -> chainInvoked.set(true));

        assertThat(chainInvoked).isTrue();
    }

    @Test
    @DisplayName("UP-5 正向：持有目标权限码的用户可访问管理端点")
    void permittedUserCanAccessManagementEndpoint() throws ServletException, IOException {
        IdentityContext.setLoginUser(loginUser(1L));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        guard(REQUIRED).doFilter(request("/actuator/metrics"), response, (req, res) -> chainInvoked.set(true));

        assertThat(chainInvoked).isTrue();
    }

    @Test
    @DisplayName("UP-5 负向：仅登录、无管理权限码的用户被拒（业务码 403）")
    void loggedInUserWithoutPermissionIsRejected() throws ServletException, IOException {
        IdentityContext.setLoginUser(loginUser(2L));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        guard(REQUIRED).doFilter(request("/actuator/metrics"), response, (req, res) -> chainInvoked.set(true));

        assertThat(chainInvoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).contains("\"code\":403", "\"success\":false");
    }

    @Test
    @DisplayName("未登录用户被拒（fail-closed）")
    void notLoggedInUserIsRejected() throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        guard(REQUIRED).doFilter(request("/actuator/metrics"), response, (req, res) -> chainInvoked.set(true));

        assertThat(chainInvoked).isFalse();
        assertThat(response.getContentAsString()).contains("\"code\":403");
    }

    @Test
    @DisplayName("未配置 required-permission：非公开管理端点一律拒绝（fail-closed）")
    void missingRequiredPermissionRejectsEverythingNonPublic() throws ServletException, IOException {
        IdentityContext.setLoginUser(loginUser(1L));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        guard("").doFilter(request("/actuator/metrics"), response, (req, res) -> chainInvoked.set(true));

        assertThat(chainInvoked).isFalse();
        assertThat(response.getContentAsString()).contains("\"code\":403");
    }

    @Test
    @DisplayName("平台超管约定（*:*:*）放行管理端点")
    void superAdminWildcardPasses() throws ServletException, IOException {
        IdentityContext.setLoginUser(loginUser(3L));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        guard(REQUIRED).doFilter(request("/actuator/metrics"), response, (req, res) -> chainInvoked.set(true));

        assertThat(chainInvoked).isTrue();
    }

    @Test
    @DisplayName("带 contextPath（如 /iot 前缀）时按去除前缀后的路径匹配")
    void contextPathIsIgnoredForMatching() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/iot/actuator/metrics");
        request.setContextPath("/iot");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        guard(REQUIRED).doFilter(request, response, (req, res) -> chainInvoked.set(true));

        assertThat(chainInvoked).isFalse();
        assertThat(response.getContentAsString()).contains("\"code\":403");
    }

    private static LoginUser loginUser(Long id) {
        LoginUser user = new LoginUser();
        user.setId(id);
        return user;
    }

    /** 权限数据源桩：1=有目标权限码，2=普通用户，3=平台超管。 */
    static class StubPermissionProvider implements PermissionProvider {

        @Override
        public List<String> getPermissions(Object loginId, String loginType) {
            String id = String.valueOf(loginId);
            if ("1".equals(id)) {
                return List.of(REQUIRED);
            }
            if ("2".equals(id)) {
                return List.of("user:list");
            }
            if ("3".equals(id)) {
                return List.of(StpPermissionAdapter.SUPER_ADMIN);
            }
            return List.of();
        }
    }
}

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.core.PermissionProvider;
import cn.ypbin.starter.security.satoken.StpPermissionAdapter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

/**
 * {@link IdentityStpLogic} 的行为测试——SF-1 的核心验收。
 *
 * <p>锁定的是「<strong>没有 Sa-Token 会话、只有网关注入的身份头</strong>」这一微服务下游场景：</p>
 * <ul>
 *     <li>有身份头且权限足够：{@code @SaCheckPermission} 放行；</li>
 *     <li>有身份头但权限不足：拒绝（{@link NotPermissionException}）；</li>
 *     <li>没有身份头：按未登录拒绝（{@link NotLoginException}）——方向必须是「拒绝」而不是「放行」；</li>
 *     <li>平台超管 {@code *:*:*} 约定必须保留（含只有两段的权限码，如 {@code user:add}）。</li>
 * </ul>
 *
 * <p>测试直接在 {@link SaInterceptor#preHandle} 上打真实注解鉴权链路（不经 MockMvc）：注解解析、
 * {@code StpLogic} 解析账号、{@link StpPermissionAdapter} 取权限、Sa-Token 通配匹配全部执行。</p>
 *
 * @author wenbin
 * @since 2026-09-24
 */
class IdentityStpLogicTest {

    private StpLogic originalStpLogic;
    private StpInterface originalStpInterface;

    @BeforeEach
    void setUp() {
        // Sa-Token 的 StpLogic 会读写请求级 Storage（如 getLoginId() 首行的 isSwitch()），
        // 纯单元测试没有 Servlet 请求上下文，因此用 Sa-Token 官方提供的 Mock 上下文
        SaTokenContextMockUtil.setMockContext();
        // Sa-Token 的账号体系是进程级静态状态：先保存原值，测试结束必须还原，避免污染同 JVM 的其它测试
        originalStpLogic = StpUtil.stpLogic;
        originalStpInterface = SaManager.getStpInterface();
        StpUtil.setStpLogic(new IdentityStpLogic());
        SaManager.setStpInterface(new StpPermissionAdapter(new StubPermissionProvider()));
    }

    @AfterEach
    void tearDown() {
        StpUtil.setStpLogic(originalStpLogic);
        SaManager.setStpInterface(originalStpInterface);
        IdentityContext.clear();
        SaTokenContextMockUtil.clearContext();
    }

    @Test
    @DisplayName("仅有身份头、无 Sa-Token 会话：有权限码放行，缺权限码拒绝")
    void annotationCheck_worksWithIdentityHeaderOnly() throws Exception {
        IdentityContext.setLoginUser(loginUser(42L));
        SaInterceptor interceptor = annotationInterceptor();

        assertThat(interceptor.preHandle(request(), response(), handler("openWindow")))
            .as("拥有 iot:window:add，应放行")
            .isTrue();
        assertThatThrownBy(() -> interceptor.preHandle(request(), response(), handler("deleteDevice")))
            .as("缺少 iot:device:delete，必须拒绝（而不是静默放行）")
            .isInstanceOf(NotPermissionException.class);
    }

    @Test
    @DisplayName("无身份头：注解鉴权按未登录拒绝，异常类型是 NotLoginException")
    void noIdentityHeader_rejectedAsNotLogin() throws Exception {
        SaInterceptor interceptor = annotationInterceptor();

        assertThatThrownBy(() -> interceptor.preHandle(request(), response(), handler("openWindow")))
            .isInstanceOf(NotLoginException.class);
        assertThatThrownBy(() -> interceptor.preHandle(request(), response(), handler("loginOnly")))
            .isInstanceOf(NotLoginException.class);
    }

    @Test
    @DisplayName("平台超管 *:*:* 约定保留：任意权限码（含只有两段的码）都放行")
    void superAdminWildcard_grantsEveryPermission() throws Exception {
        IdentityContext.setLoginUser(loginUser(1L));
        SaInterceptor interceptor = annotationInterceptor();

        assertThat(interceptor.preHandle(request(), response(), handler("twoSegment")))
            .as("*:*:* 归一化后必须能命中两段权限码 user:add")
            .isTrue();
        assertThat(interceptor.preHandle(request(), response(), handler("openWindow")))
            .as("*:*:* 归一化后必须能命中三段权限码 iot:window:add")
            .isTrue();
    }

    @Test
    @DisplayName("@SaCheckLogin 在仅有身份头时通过")
    void checkLogin_passesWithIdentityHeaderOnly() throws Exception {
        IdentityContext.setLoginUser(loginUser(42L));

        assertThat(annotationInterceptor().preHandle(request(), response(), handler("loginOnly")))
            .isTrue();
    }

    @Test
    @DisplayName("配置了 sa-token.token-prefix 也不影响身份头解析")
    void tokenPrefixConfigured_doesNotBreakIdentityResolution() {
        String originalPrefix = SaManager.getConfig().getTokenPrefix();
        try {
            SaManager.getConfig().setTokenPrefix("Bearer");
            IdentityContext.setLoginUser(loginUser(42L));

            assertThat(StpUtil.getLoginId()).isEqualTo("42");
            assertThat(StpUtil.isLogin()).isTrue();
        } finally {
            SaManager.getConfig().setTokenPrefix(originalPrefix);
            IdentityContext.clear();
        }
    }

    @Test
    @DisplayName("无身份头时 StpUtil 语义等价于未登录")
    void withoutIdentityHeader_stpUtilReportsNotLogin() {
        assertThat(StpUtil.isLogin()).isFalse();
        assertThatThrownBy(StpUtil::getLoginId).isInstanceOf(NotLoginException.class);
    }

    @Test
    @DisplayName("按 token 反查只认当前请求身份：不会把调用者身份冒充成其它 token 的主人")
    void tokenLookup_doesNotImpersonateCaller() {
        IdentityContext.setLoginUser(loginUser(42L));

        assertThat(StpUtil.getStpLogic().isValidToken("999")).isFalse();
        assertThat(StpUtil.getStpLogic().getLoginIdByToken("999")).isNull();
        assertThat(StpUtil.getStpLogic().getLoginIdByTokenNotThinkFreeze("999")).isNull();
    }

    private static SaInterceptor annotationInterceptor() {
        return new SaInterceptor().isAnnotation(true);
    }

    private static HttpServletRequest request() {
        return mock(HttpServletRequest.class);
    }

    private static HttpServletResponse response() {
        return mock(HttpServletResponse.class);
    }

    private static HandlerMethod handler(String methodName) throws NoSuchMethodException {
        Method method = DemoController.class.getMethod(methodName);
        return new HandlerMethod(new DemoController(), method);
    }

    private static LoginUser loginUser(Long id) {
        LoginUser user = new LoginUser();
        user.setId(id);
        return user;
    }

    /** 注解鉴权目标：覆盖两段/三段权限码与 @SaCheckLogin。 */
    static class DemoController {

        @SaCheckPermission("iot:window:add")
        public String openWindow() {
            return "ok";
        }

        @SaCheckPermission("iot:device:delete")
        public String deleteDevice() {
            return "ok";
        }

        @SaCheckPermission("user:add")
        public String twoSegment() {
            return "ok";
        }

        @SaCheckLogin
        public String loginOnly() {
            return "ok";
        }
    }

    /** 权限数据源桩：账号 1 = 平台超管（*:*:*），账号 42 = 只有 iot:window:add。 */
    static class StubPermissionProvider implements PermissionProvider {

        @Override
        public List<String> getPermissions(Object loginId, String loginType) {
            String id = String.valueOf(loginId);
            if ("1".equals(id)) {
                return List.of(StpPermissionAdapter.SUPER_ADMIN);
            }
            if ("42".equals(id)) {
                return List.of("iot:window:add");
            }
            return List.of();
        }
    }
}

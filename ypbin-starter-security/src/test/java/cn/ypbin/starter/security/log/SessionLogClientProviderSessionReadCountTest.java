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
package cn.ypbin.starter.security.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mockStatic;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.ypbin.starter.log.core.LogClientProvider;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.security.identity.IdentityContext;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * {@link SessionLogClientProvider} 两种模式的<b>会话读取次数</b>测试（技术债 2）。
 *
 * <p><b>要钉住的性能债</b>：旧实现在单体模式下先 {@code getUserIdSafely()}（内部读会话取 loginId）、
 * 再 {@code StpUtil.getSessionByLoginId(userId, false)}（又读一次账号会话）——同一次操作日志采集读
 * <b>2 次</b>会话。而单体模式的 {@code LoginUser} 就在当前 token 会话里，第二次读纯属多余。
 * 改用按模式分支后单体模式应为 <b>1 次</b>，且<b>绝不</b>再调 {@code getSessionByLoginId}。</p>
 *
 * <p><b>为什么用 {@code mockStatic(StpUtil.class)}</b>：「会话读取次数」是调用次数属性，
 * 行为断言（返回了什么）无法证伪「多读了一次」——只有计数才能。这是本次债唯一可靠的证据形式。</p>
 *
 * <p><b>身份头模式必须保持读会话</b>：身份头只有 id/username/tenantId/deptId/roles，
 * 没有 client 三元组，{@code LoginUser} 的三元组只能从账号会话取；故该模式仍必须
 * 调用 {@code getSessionByLoginId}——测试同时反向钉住「不能为了省一次读而把该模式也改坏」。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class SessionLogClientProviderSessionReadCountTest {

    private static final long IDENTITY_USER_ID = 3003L;

    private static final long SESSION_USER_ID = 4004L;

    private final SessionLogClientProvider provider = new SessionLogClientProvider();

    private StpLogic originalStpLogic;

    private SaTokenDao originalSaTokenDao;

    @BeforeEach
    void setUp() {
        SaTokenContextMockUtil.setMockContext();
        originalStpLogic = StpUtil.stpLogic;
        originalSaTokenDao = SaManager.getSaTokenDao();
        StpUtil.setStpLogic(new StpLogic(StpUtil.TYPE));
        SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());
        IdentityContext.clear();
    }

    @AfterEach
    void tearDown() {
        StpUtil.logout();
        StpUtil.setStpLogic(originalStpLogic);
        SaManager.setSaTokenDao(originalSaTokenDao);
        IdentityContext.clear();
        SaTokenContextMockUtil.clearContext();
    }

    // ------------------------------------------------------------------ 单体模式：1 次会话读

    @Test
    @DisplayName("★★ 单体模式：不再调用 StpUtil.getSessionByLoginId（消除多余会话读）")
    void singleModeShouldNotCallGetSessionByLoginId() {
        loginWithSession(SESSION_USER_ID);
        writeCurrentSessionLoginUser(loginUserWithClient(SESSION_USER_ID, "web-admin", "PC", "password"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class,
            org.mockito.Mockito.CALLS_REAL_METHODS)) {
            Optional<LogClientProvider.LogClientInfo> result = provider.getCurrentClient();

            assertThat(result).isPresent();
            assertThat(result.orElseThrow().clientId()).isEqualTo("web-admin");
            stp.verify(() -> StpUtil.getSessionByLoginId(any(), anyBoolean()),
                org.mockito.Mockito.never());
        }
    }

    @Test
    @DisplayName("★ 单体模式：只读一次当前 token 会话（StpUtil.getSession 计数为 1）")
    void singleModeShouldReadCurrentSessionExactlyOnce() {
        loginWithSession(SESSION_USER_ID);
        writeCurrentSessionLoginUser(loginUserWithClient(SESSION_USER_ID, "web-admin", "PC", "password"));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class,
            org.mockito.Mockito.CALLS_REAL_METHODS)) {
            assertThat(provider.getCurrentClient()).isPresent();

            stp.verify(() -> StpUtil.getSession(), org.mockito.Mockito.times(1));
        }
    }

    @Test
    @DisplayName("单体模式：会话中三元组全空 ⇒ 返回空（不臆造取值）")
    void singleModeShouldReturnEmptyWhenClientFieldsBlank() {
        loginWithSession(SESSION_USER_ID);
        writeCurrentSessionLoginUser(loginUser(SESSION_USER_ID));

        assertThat(provider.getCurrentClient()).isEmpty();
    }

    @Test
    @DisplayName("单体模式：未登录 ⇒ 返回空，且不产生会话读")
    void singleModeNotLoggedInShouldReturnEmpty() {
        assertThat(provider.getCurrentClient()).isEmpty();
    }

    // ------------------------------------------------------------------ 身份头模式：仍读账号会话

    @Test
    @DisplayName("★★ 身份头模式：必须读账号会话（身份头无 client 三元组，不能省这一次）")
    void identityModeShouldStillReadAccountSession() {
        IdentityContext.setLoginUser(identityLoginUser(IDENTITY_USER_ID));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class,
            org.mockito.Mockito.CALLS_REAL_METHODS)) {
            // 账号会话读不到（未共用会话存储）：返回空，但调用必须发生
            assertThat(provider.getCurrentClient()).isEmpty();

            stp.verify(() -> StpUtil.getSessionByLoginId(any(), anyBoolean()),
                org.mockito.Mockito.times(1));
        }
    }

    @Test
    @DisplayName("身份头模式：账号会话中有三元组时能取到（按 loginId 跨会话读的既定行为不变）")
    void identityModeShouldResolveClientFromAccountSession() {
        IdentityContext.setLoginUser(identityLoginUser(IDENTITY_USER_ID));
        // 模拟 auth 登录收尾：按 loginId 写入账号会话
        SaSession accountSession = StpUtil.getSessionByLoginId(IDENTITY_USER_ID, true);
        accountSession.set(UserContext.KEY_LOGIN_USER,
            loginUserWithClient(IDENTITY_USER_ID, "app-ios", "APP", "sms"));

        Optional<LogClientProvider.LogClientInfo> result = provider.getCurrentClient();

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().clientId()).isEqualTo("app-ios");
        assertThat(result.orElseThrow().authType()).isEqualTo("sms");
    }

    // ------------------------------------------------------------------ 失败语义保持

    @Test
    @DisplayName("身份头模式：账号会话中登录用户类型不可识别 ⇒ 返回空（原失败语义不变）")
    void identityModeShouldReturnEmptyWhenSessionValueUnrecognized() {
        IdentityContext.setLoginUser(identityLoginUser(IDENTITY_USER_ID));
        SaSession accountSession = StpUtil.getSessionByLoginId(IDENTITY_USER_ID, true);
        accountSession.set(UserContext.KEY_LOGIN_USER, "not-a-login-user");

        assertThat(provider.getCurrentClient()).isEmpty();
    }

    // ------------------------------------------------------------------ 辅助

    private static void loginWithSession(long userId) {
        StpUtil.login(userId);
    }

    private static void writeCurrentSessionLoginUser(LoginUser loginUser) {
        StpUtil.getSession().set(UserContext.KEY_LOGIN_USER, loginUser);
    }

    private static LoginUser loginUser(long id) {
        LoginUser user = new LoginUser();
        user.setId(id);
        user.setUsername("user-" + id);
        return user;
    }

    private static LoginUser loginUserWithClient(long id, String clientId, String clientType,
                                                 String authType) {
        LoginUser user = loginUser(id);
        user.setClientId(clientId);
        user.setClientType(clientType);
        user.setAuthType(authType);
        return user;
    }

    /**
     * 身份头模式下的登录用户：身份头只有 id/username/tenantId/deptId/roles，<b>不含</b> client 三元组。
     */
    private static LoginUser identityLoginUser(long id) {
        return loginUser(id);
    }
}

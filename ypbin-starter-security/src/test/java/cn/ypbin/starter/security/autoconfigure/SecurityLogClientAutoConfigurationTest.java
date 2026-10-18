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
package cn.ypbin.starter.security.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.ypbin.starter.log.core.LogClientProvider;
import cn.ypbin.starter.log.core.LogUserProvider;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.security.identity.IdentityContext;
import cn.ypbin.starter.security.log.SessionLogClientProvider;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * {@link SecurityLogClientAutoConfiguration} 默认 provider 的优先级契约测试。
 *
 * <p>本类把 #100 引入的行为变更钉成契约——<strong>尤其是「身份头优先于会话」这一条</strong>：
 * 同一请求上若既有身份头上下文（微服务下游）又有 Sa-Token 会话（单体/残留会话），
 * 操作日志的 {@code userId} 必须取<strong>身份头</strong>的值。三条用例分别覆盖
 * (a) 仅身份头、(b) 仅会话、(c) 两者共存：缺任一条，优先级都可能在下一次重构中静默翻转，
 * 而这类翻转没有任何编译期或启动期信号。</p>
 *
 * <p>测试用 Sa-Token 官方 Mock 上下文（{@link SaTokenContextMockUtil}）承载真实会话，并逐用例替换
 * 会话存储（{@link SaTokenDaoDefaultImpl}）：Sa-Token 的账号体系与 DAO 是进程级静态状态，
 * 不隔离会让同 JVM 的其它测试串用会话（与 {@code IdentityStpLogicTest} 同口径）。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class SecurityLogClientAutoConfigurationTest {

    /** 身份头（网关签发）里的用户 ID，与下面的会话用户 ID 刻意不同，用于区分「取了哪一个」。 */
    private static final long IDENTITY_USER_ID = 1001L;

    /** Sa-Token 会话中的用户 ID。 */
    private static final long SESSION_USER_ID = 2002L;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SecurityLogClientAutoConfiguration.class));

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

    @Test
    @DisplayName("(a) 仅身份头（无 Sa-Token 会话）：userId 取自身份头")
    void identityHeaderOnly_usesIdentityUserId() {
        IdentityContext.setLoginUser(loginUser(IDENTITY_USER_ID));

        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(LogUserProvider.class).getCurrentUserId())
                .as("身份头模式下必须能取到 userId（旧实现只读会话 ⇒ 恒为空）")
                .contains(IDENTITY_USER_ID);
        });
    }

    @Test
    @DisplayName("(b) 仅会话（无身份头）：userId 回退 Sa-Token 会话")
    void sessionOnly_fallsBackToSessionUserId() {
        loginWithSession(SESSION_USER_ID);

        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(LogUserProvider.class).getCurrentUserId())
                .as("单体模式下回退会话取值，行为与旧实现一致")
                .contains(SESSION_USER_ID);
        });
    }

    @Test
    @DisplayName("(c) 身份头与会话同时存在：必须返回身份头 userId（优先级契约）")
    void bothPresent_prefersIdentityHeaderUserId() {
        loginWithSession(SESSION_USER_ID);
        IdentityContext.setLoginUser(loginUser(IDENTITY_USER_ID));

        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(LogUserProvider.class).getCurrentUserId())
                .as("两者共存时必须取身份头 userId（%s），而不是会话 userId（%s）",
                    IDENTITY_USER_ID, SESSION_USER_ID)
                .contains(IDENTITY_USER_ID);
        });
    }

    @Test
    @DisplayName("客户端信息：从账号会话的 LoginUser 取三元组（身份头模式下同样有效）")
    void clientProvider_readsClientInfoFromAccountSession() {
        // 微服务下游的真实形态：身份头在场（本服务没有自己的 token），
        // 账号会话由 auth 登录时写入、按 loginId（= 身份头 userId）可读（共用会话存储）。
        loginWithSession(SESSION_USER_ID);
        writeSessionLoginUser(loginUserWithClient(SESSION_USER_ID, "web-admin", "WEB", "ACCOUNT"));
        IdentityContext.setLoginUser(loginUser(SESSION_USER_ID));

        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(LogClientProvider.class))
                .as("默认实现应为会话账号实现，而不是只读当前身份上下文")
                .isInstanceOf(SessionLogClientProvider.class);
            assertThat(context.getBean(LogClientProvider.class).getCurrentClient())
                .as("身份头不含客户端字段，三元组必须来自账号会话（旧实现恒空）")
                .contains(new LogClientProvider.LogClientInfo("web-admin", "WEB", "ACCOUNT"));
        });
    }

    @Test
    @DisplayName("客户端信息：按 userId 读不到账号会话时返回空（字段留空是事实，不臆造）")
    void clientProvider_emptyWhenAccountSessionMissing() {
        // 身份头 userId 与已存在的账号会话 loginId 不一致（如本服务未共用会话存储）
        loginWithSession(SESSION_USER_ID);
        writeSessionLoginUser(loginUserWithClient(SESSION_USER_ID, "web-admin", "WEB", "ACCOUNT"));
        IdentityContext.setLoginUser(loginUser(IDENTITY_USER_ID));

        runner.run(context -> assertThat(context.getBean(LogClientProvider.class).getCurrentClient()).isEmpty());
    }

    @Test
    @DisplayName("客户端信息：未登录时为空且不报错")
    void clientProvider_emptyWhenNotLoggedIn() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(LogClientProvider.class).getCurrentClient()).isEmpty();
            assertThat(context.getBean(LogUserProvider.class).getCurrentUserId()).isEmpty();
        });
    }

    @Test
    @DisplayName("登录会话里未回填客户端信息：返回空（不臆造取值）")
    void clientProvider_emptyWhenSessionHasNoClientFields() {
        loginWithSession(SESSION_USER_ID);
        writeSessionLoginUser(loginUser(SESSION_USER_ID));

        runner.run(context -> assertThat(context.getBean(LogClientProvider.class).getCurrentClient()).isEmpty());
    }

    @Test
    @DisplayName("宿主自定义 provider 时两个默认实现都退让（可覆盖语义不变）")
    void hostProviders_winOverDefaults() {
        LogUserProvider customUser = () -> Optional.of(9L);
        LogClientProvider customClient =
            () -> Optional.of(new LogClientProvider.LogClientInfo("custom", "APP", "PHONE"));

        runner.withBean(LogUserProvider.class, () -> customUser)
            .withBean(LogClientProvider.class, () -> customClient)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(LogUserProvider.class)).isSameAs(customUser);
                assertThat(context.getBean(LogClientProvider.class)).isSameAs(customClient);
            });
    }

    /**
     * 建立真实 Sa-Token 登录会话（Mock 请求上下文 + 内存会话存储）。
     *
     * @param userId 用户 ID
     */
    private static void loginWithSession(long userId) {
        StpUtil.login(userId);
    }

    /**
     * 把登录用户信息写入当前账号会话（模拟 auth 服务登录收尾的动作）。
     *
     * @param loginUser 登录用户
     */
    private static void writeSessionLoginUser(LoginUser loginUser) {
        StpUtil.getSession().set(UserContext.KEY_LOGIN_USER, loginUser);
    }

    private static LoginUser loginUser(long id) {
        LoginUser user = new LoginUser();
        user.setId(id);
        user.setUsername("user-" + id);
        return user;
    }

    private static LoginUser loginUserWithClient(long id, String clientId, String clientType, String authType) {
        LoginUser user = loginUser(id);
        user.setClientId(clientId);
        user.setClientType(clientType);
        user.setAuthType(authType);
        return user;
    }
}

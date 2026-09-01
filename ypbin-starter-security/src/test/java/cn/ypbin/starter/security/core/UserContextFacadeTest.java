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
package cn.ypbin.starter.security.core;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.security.identity.IdentityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link UserContext} 双模式自适应门面测试。
 *
 * <p>验证：微服务身份头模式（IdentityContext 有值）优先；无身份头时安全回退（不抛异常）。
 *
 * @author wenbin
 * @since 2026-09-01
 */
class UserContextFacadeTest {

    @AfterEach
    void tearDown() {
        IdentityContext.clear();
    }

    private LoginUser identityUser(long id, String username, long tenantId) {
        LoginUser user = new LoginUser();
        user.setId(id);
        user.setUsername(username);
        user.setTenantId(tenantId);
        return user;
    }

    @Test
    void shouldReadFromIdentityContextWhenPresent() {
        IdentityContext.setLoginUser(identityUser(42L, "alice", 7L));

        assertThat(UserContext.getUserId()).isEqualTo(42L);
        assertThat(UserContext.getUserIdSafely()).contains(42L);
        assertThat(UserContext.getUsername()).contains("alice");
        assertThat(UserContext.getTenantId()).contains(7L);
        assertThat(UserContext.isLogin()).isTrue();
        assertThat(UserContext.getLoginUser()).isPresent();
    }

    @Test
    void shouldReturnEmptyWithoutAnyContext() {
        // 无身份头、无 sa-token 会话（测试线程无 Web 上下文）：安全返回空，不抛异常
        assertThat(UserContext.getUserId()).isNull();
        assertThat(UserContext.getUserIdSafely()).isEmpty();
        assertThat(UserContext.getUsername()).isEmpty();
        assertThat(UserContext.getTenantId()).isEmpty();
        assertThat(UserContext.isLogin()).isFalse();
        assertThat(UserContext.getLoginUser()).isEmpty();
    }

    @Test
    void identityContextShouldTakePriorityOverSession() {
        // 身份头优先：即使 sa-token 会话无值（无 Web 上下文），身份头有值即返回
        IdentityContext.setLoginUser(identityUser(99L, "bob", 3L));

        assertThat(UserContext.getUserId()).isEqualTo(99L);
        assertThat(UserContext.getTenantId()).contains(3L);
    }
}

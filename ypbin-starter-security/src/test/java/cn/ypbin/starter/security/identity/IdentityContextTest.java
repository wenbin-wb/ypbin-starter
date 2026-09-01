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
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link IdentityContext} 单元测试。
 *
 * @author wenbin
 * @since 2026-09-01
 */
class IdentityContextTest {

    @AfterEach
    void tearDown() {
        IdentityContext.clear();
    }

    private LoginUser buildUser() {
        LoginUser user = new LoginUser();
        user.setId(100L);
        user.setUsername("alice");
        user.setTenantId(1L);
        user.setDeptId(2L);
        user.setRoles(Set.of("admin", "user"));
        return user;
    }

    @Test
    void shouldSetAndGetLoginUser() {
        LoginUser user = buildUser();
        IdentityContext.setLoginUser(user);

        assertThat(IdentityContext.getLoginUser()).isPresent();
        assertThat(IdentityContext.getLoginUser().orElseThrow().getUsername()).isEqualTo("alice");
    }

    @Test
    void shouldBeEmptyWhenNotLogin() {
        assertThat(IdentityContext.getLoginUser()).isEmpty();
        assertThat(IdentityContext.getUserId()).isEmpty();
        assertThat(IdentityContext.getUsername()).isEmpty();
        assertThat(IdentityContext.getTenantId()).isEmpty();
        assertThat(IdentityContext.isLogin()).isFalse();
    }

    @Test
    void shouldReadFieldsFromLoginUser() {
        IdentityContext.setLoginUser(buildUser());

        assertThat(IdentityContext.isLogin()).isTrue();
        assertThat(IdentityContext.getUserId()).contains(100L);
        assertThat(IdentityContext.getUsername()).contains("alice");
        assertThat(IdentityContext.getTenantId()).contains(1L);
    }

    @Test
    void shouldClearContext() {
        IdentityContext.setLoginUser(buildUser());
        IdentityContext.clear();

        assertThat(IdentityContext.isLogin()).isFalse();
        assertThat(IdentityContext.getUserId()).isEmpty();
    }
}

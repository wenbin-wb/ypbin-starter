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

import cn.ypbin.starter.security.client.LoginClient;
import org.junit.jupiter.api.Test;

/**
 * 登录用户模型测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class LoginHelperTest {

    @Test
    void loginUserShouldCarryFields() {
        LoginUser user = new LoginUser(1L, "admin");
        user.setNickname("管理员");
        user.setTenantId(2L);
        assertThat(user.getId()).isEqualTo(1L);
        assertThat(user.getUsername()).isEqualTo("admin");
        assertThat(user.getNickname()).isEqualTo("管理员");
        assertThat(user.getTenantId()).isEqualTo(2L);
        LoginUser empty = new LoginUser();
        assertThat(empty.getId()).isNull();
    }

    @Test
    void loginClientShouldCarryFields() {
        LoginClient client = new LoginClient();
        client.setClientId("web-admin");
        client.setAuthTypes(java.util.Set.of("ACCOUNT"));
        assertThat(client.getClientId()).isEqualTo("web-admin");
        assertThat(client.getAuthTypes()).contains("ACCOUNT");
    }

}

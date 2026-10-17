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
package cn.ypbin.starter.tenant.provider;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.identity.IdentityContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link IdentityTenantProvider} 单元测试：取值为纯身份上下文透传，空态一律返回 empty。
 *
 * @author wenbin
 * @since 2026-10-17
 */
class IdentityTenantProviderTest {

    private final IdentityTenantProvider provider = new IdentityTenantProvider();

    /**
     * {@link IdentityContext} 是 ThreadLocal 语义，必须逐用例清理，避免污染其它用例。
     */
    @AfterEach
    void clearIdentityContext() {
        IdentityContext.clear();
    }

    @Test
    @DisplayName("身份携带租户：返回该租户 ID")
    void shouldReturnTenantIdFromIdentity() {
        IdentityContext.setLoginUser(loginUser(100L, 7L));

        assertThat(provider.getCurrentTenantId()).contains(7L);
    }

    @Test
    @DisplayName("身份未携带租户：返回 empty，不猜测兜底租户")
    void shouldReturnEmptyWhenIdentityHasNoTenant() {
        IdentityContext.setLoginUser(loginUser(100L, null));

        assertThat(provider.getCurrentTenantId()).isEmpty();
    }

    @Test
    @DisplayName("无身份（未登录/异步无上下文）：返回 empty")
    void shouldReturnEmptyWhenNoIdentity() {
        assertThat(provider.getCurrentTenantId()).isEmpty();
    }

    private static LoginUser loginUser(Long id, Long tenantId) {
        LoginUser user = new LoginUser();
        user.setId(id);
        user.setTenantId(tenantId);
        return user;
    }
}

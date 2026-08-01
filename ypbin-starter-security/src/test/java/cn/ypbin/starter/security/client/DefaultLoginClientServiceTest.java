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
package cn.ypbin.starter.security.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.dev33.satoken.stp.parameter.enums.SaLogoutMode;
import cn.dev33.satoken.stp.parameter.enums.SaReplacedLoginExitMode;
import cn.dev33.satoken.stp.parameter.enums.SaReplacedRange;
import cn.ypbin.starter.security.autoconfigure.SecurityProperties;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link DefaultLoginClientService} 单元测试。
 *
 * @author wenbin
 * @since 2026-08-01
 */
class DefaultLoginClientServiceTest {

    @Test
    void requireClientShouldUseConfiguredPolicy() {
        LoginClient client = client();
        DefaultLoginClientService service = service(client);

        LoginClientRequest request = new LoginClientRequest("web-admin", "account");
        request.setClientSecret("secret");
        LoginClient actual = service.requireClient(request);
        SaLoginParameter parameter = service.buildLoginParameter(actual, request);

        assertThat(actual.getClientId()).isEqualTo("web-admin");
        assertThat(request.getAuthType()).isEqualTo("ACCOUNT");
        assertThat(parameter.getTimeout()).isEqualTo(3600);
        assertThat(parameter.getActiveTimeout()).isEqualTo(600);
        assertThat(parameter.getIsConcurrent()).isFalse();
        assertThat(parameter.getIsShare()).isFalse();
        assertThat(parameter.getMaxLoginCount()).isEqualTo(2);
        assertThat(parameter.getReplacedRange()).isEqualTo(SaReplacedRange.ALL_DEVICE_TYPE);
        assertThat(parameter.getReplacedLoginExitMode()).isEqualTo(SaReplacedLoginExitMode.OLD_DEVICE);
        assertThat(parameter.getOverflowLogoutMode()).isEqualTo(SaLogoutMode.KICKOUT);
        assertThat(parameter.getExtra("clientId")).isEqualTo("web-admin");
        assertThat(parameter.getExtra("authType")).isEqualTo("ACCOUNT");
    }

    @Test
    void requireClientShouldRejectUnknownClient() {
        DefaultLoginClientService service = service(client());

        assertThatThrownBy(() -> service.requireClient(new LoginClientRequest("missing", "ACCOUNT")))
            .isInstanceOf(LoginClientException.class)
            .hasMessage("客户端不存在");
    }

    @Test
    void requireClientShouldRejectDisabledClient() {
        LoginClient client = client();
        client.setEnabled(false);
        DefaultLoginClientService service = service(client);

        assertThatThrownBy(() -> service.requireClient(new LoginClientRequest("web-admin", "ACCOUNT")))
            .isInstanceOf(LoginClientException.class)
            .hasMessage("客户端已禁用");
    }

    @Test
    void requireClientShouldRejectUnsupportedAuthType() {
        DefaultLoginClientService service = service(client());
        LoginClientRequest request = new LoginClientRequest("web-admin", "SOCIAL");
        request.setClientSecret("secret");

        assertThatThrownBy(() -> service.requireClient(request))
            .isInstanceOf(LoginClientException.class)
            .hasMessage("客户端不支持当前认证方式");
    }

    @Test
    void requireClientShouldRejectWrongSecret() {
        LoginClientRequest request = new LoginClientRequest("web-admin", "ACCOUNT");
        request.setClientSecret("bad");
        DefaultLoginClientService service = service(client());

        assertThatThrownBy(() -> service.requireClient(request))
            .isInstanceOf(LoginClientException.class)
            .hasMessage("客户端认证失败");
    }

    @Test
    void requireClientShouldUseDefaultClientId() {
        DefaultLoginClientService service = service(client());
        LoginClientRequest request = new LoginClientRequest(null, "ACCOUNT");
        request.setClientSecret("secret");

        LoginClient actual = service.requireClient(request);

        assertThat(actual.getClientId()).isEqualTo("web-admin");
        assertThat(request.getClientId()).isEqualTo("web-admin");
    }

    private DefaultLoginClientService service(LoginClient client) {
        SecurityProperties properties = new SecurityProperties();
        properties.setDefaultClientId("web-admin");
        properties.setClients(List.of(client));
        return new DefaultLoginClientService(new DefaultLoginClientProvider(properties), properties);
    }

    private LoginClient client() {
        LoginClient client = new LoginClient();
        client.setClientId("web-admin");
        client.setClientSecret("secret");
        client.setClientType("WEB");
        client.setAuthTypes(new LinkedHashSet<>(Set.of("ACCOUNT", "PHONE")));
        client.setTimeout(3600L);
        client.setActiveTimeout(600L);
        client.setConcurrent(false);
        client.setShare(false);
        client.setMaxLoginCount(2);
        client.setReplacedRange(SaReplacedRange.ALL_DEVICE_TYPE);
        client.setReplacedLoginExitMode(SaReplacedLoginExitMode.OLD_DEVICE);
        client.setOverflowLogoutMode(SaLogoutMode.KICKOUT);
        client.setEnabled(true);
        return client;
    }
}

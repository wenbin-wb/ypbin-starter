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

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.ypbin.starter.security.autoconfigure.SecurityProperties;
import java.util.Locale;

/**
 * 默认登录客户端运行时服务。
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class DefaultLoginClientService implements LoginClientService {

    private final LoginClientProvider provider;
    private final SecurityProperties properties;

    public DefaultLoginClientService(LoginClientProvider provider, SecurityProperties properties) {
        this.provider = provider;
        this.properties = properties;
    }

    @Override
    public String getDefaultClientId() {
        return properties.getDefaultClientId();
    }

    @Override
    public LoginClient requireClient(LoginClientRequest request) {
        LoginClientRequest actual = normalize(request);
        if (!properties.isClientEnabled()) {
            return disabledCheckOnlyClient(actual);
        }
        LoginClient client = provider.findByClientId(actual.getClientId())
            .orElseThrow(() -> new LoginClientException("客户端不存在"));
        validate(client, actual);
        return client;
    }

    @Override
    public SaLoginParameter buildLoginParameter(LoginClient client, LoginClientRequest request) {
        LoginClientRequest actual = normalize(request);
        SaLoginParameter parameter = SaLoginParameter.create()
            .setDeviceType(normalizeDeviceType(client.getClientType()))
            .setExtra("clientId", client.getClientId())
            .setExtra("clientType", client.getClientType())
            .setExtra("authType", actual.getAuthType())
            .setTerminalExtra("clientId", client.getClientId())
            .setTerminalExtra("clientType", client.getClientType())
            .setTerminalExtra("authType", actual.getAuthType());
        if (actual.getDeviceId() != null && !actual.getDeviceId().isBlank()) {
            parameter.setDeviceId(actual.getDeviceId());
        }
        if (client.getTimeout() != null) {
            parameter.setTimeout(client.getTimeout());
        }
        if (client.getActiveTimeout() != null) {
            parameter.setActiveTimeout(client.getActiveTimeout());
        }
        if (client.getConcurrent() != null) {
            parameter.setIsConcurrent(client.getConcurrent());
        }
        if (client.getShare() != null) {
            parameter.setIsShare(client.getShare());
        }
        if (client.getMaxLoginCount() != null) {
            parameter.setMaxLoginCount(client.getMaxLoginCount());
        }
        if (client.getReplacedRange() != null) {
            parameter.setReplacedRange(client.getReplacedRange());
        }
        if (client.getReplacedLoginExitMode() != null) {
            parameter.setReplacedLoginExitMode(client.getReplacedLoginExitMode());
        }
        if (client.getOverflowLogoutMode() != null) {
            parameter.setOverflowLogoutMode(client.getOverflowLogoutMode());
        }
        if (client.getLastingCookie() != null) {
            parameter.setIsLastingCookie(client.getLastingCookie());
        }
        if (client.getWriteHeader() != null) {
            parameter.setIsWriteHeader(client.getWriteHeader());
        }
        return parameter;
    }

    @Override
    public LoginClient login(Long userId, LoginClientRequest request) {
        LoginClient client = requireClient(request);
        StpUtil.login(userId, buildLoginParameter(client, request));
        return client;
    }

    private void validate(LoginClient client, LoginClientRequest request) {
        if (!client.isEnabled()) {
            throw new LoginClientException("客户端已禁用");
        }
        if (client.getClientSecret() != null && !client.getClientSecret().isBlank()
            && !client.getClientSecret().equals(request.getClientSecret())) {
            throw new LoginClientException("客户端认证失败");
        }
        if (request.getAuthType() != null && !request.getAuthType().isBlank()
            && client.getAuthTypes() != null && !client.getAuthTypes().isEmpty()
            && client.getAuthTypes().stream()
                .map(authType -> authType.toUpperCase(Locale.ROOT))
                .noneMatch(request.getAuthType()::equals)) {
            throw new LoginClientException("客户端不支持当前认证方式");
        }
    }

    private LoginClientRequest normalize(LoginClientRequest request) {
        LoginClientRequest actual = request == null ? new LoginClientRequest() : request;
        if (actual.getClientId() == null || actual.getClientId().isBlank()) {
            actual.setClientId(properties.getDefaultClientId());
        }
        if (actual.getAuthType() != null) {
            actual.setAuthType(actual.getAuthType().toUpperCase(Locale.ROOT));
        }
        return actual;
    }

    private LoginClient disabledCheckOnlyClient(LoginClientRequest request) {
        LoginClient client = new LoginClient();
        client.setClientId(request.getClientId());
        client.setClientType("WEB");
        return client;
    }

    private String normalizeDeviceType(String clientType) {
        return (clientType == null || clientType.isBlank()) ? "WEB" : clientType.toLowerCase(Locale.ROOT);
    }
}

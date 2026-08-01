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

import cn.ypbin.starter.security.autoconfigure.SecurityProperties;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 配置文件版登录客户端配置来源。
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class DefaultLoginClientProvider implements LoginClientProvider {

    private final Map<String, LoginClient> clients;

    public DefaultLoginClientProvider(SecurityProperties properties) {
        this.clients = properties.getClients().stream()
            .filter(client -> client.getClientId() != null && !client.getClientId().isBlank())
            .collect(Collectors.toMap(LoginClient::getClientId, Function.identity(), (left, right) -> right));
    }

    @Override
    public Optional<LoginClient> findByClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(clients.get(clientId));
    }
}

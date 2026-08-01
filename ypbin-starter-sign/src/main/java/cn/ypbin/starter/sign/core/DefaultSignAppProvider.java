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
package cn.ypbin.starter.sign.core;

import cn.ypbin.starter.sign.autoconfigure.SignProperties;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 配置文件版开放应用来源。
 *
 * <p>读取 {@code ypbin.sign.apps} 配置为应用索引。业务系统有应用管理表时实现自定义
 * {@link SignAppProvider} 从数据库加载即可覆盖本实现。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class DefaultSignAppProvider implements SignAppProvider {

    private final Map<String, SignApp> apps;

    public DefaultSignAppProvider(SignProperties properties) {
        this.apps = properties.getApps().stream()
            .filter(app -> app.getAccessKey() != null && !app.getAccessKey().isBlank())
            .collect(Collectors.toMap(SignProperties.AppInfo::getAccessKey, DefaultSignAppProvider::toSignApp,
                (left, right) -> right));
    }

    @Override
    public Optional<SignApp> findByAccessKey(String accessKey) {
        if (accessKey == null || accessKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(apps.get(accessKey));
    }

    private static SignApp toSignApp(SignProperties.AppInfo info) {
        SignApp app = new SignApp(info.getAccessKey(), info.getSecretKey());
        app.setAppName(info.getAppName());
        app.setExpireTime(info.getExpireTime());
        app.setEnabled(info.isEnabled());
        return app;
    }
}

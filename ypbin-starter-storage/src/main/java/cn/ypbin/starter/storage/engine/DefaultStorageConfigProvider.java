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
package cn.ypbin.starter.storage.engine;

import cn.ypbin.starter.storage.autoconfigure.StorageProperties;
import cn.ypbin.starter.storage.autoconfigure.StorageProperties.LocalConfig;
import cn.ypbin.starter.storage.autoconfigure.StorageProperties.OssConfig;
import java.util.List;

/**
 * 配置文件版存储源配置来源。
 *
 * <p>读取 {@code ypbin.storage.*} 绑定的 {@link StorageProperties}。业务系统把存储源做成后台可配置时，
 * 实现自定义 {@link StorageConfigProvider} 覆盖本实现即可。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class DefaultStorageConfigProvider implements StorageConfigProvider {

    private final StorageProperties properties;

    public DefaultStorageConfigProvider(StorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<LocalConfig> getLocalConfigs() {
        return properties.getLocal();
    }

    @Override
    public List<OssConfig> getOssConfigs() {
        return properties.getOss();
    }

    @Override
    public String getDefaultPlatform() {
        return properties.getDefaultPlatform();
    }
}

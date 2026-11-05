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
package cn.ypbin.starter.storage.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.storage.autoconfigure.StorageProperties.LocalConfig;
import cn.ypbin.starter.storage.engine.DefaultStorageConfigProvider;
import cn.ypbin.starter.storage.strategy.StorageStrategy;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 存储模块零配置本地源行为用例。
 *
 * @author wenbin
 * @since 2026-11-05
 */
class StorageAutoConfigurationTest {

    private final StorageAutoConfiguration configuration = new StorageAutoConfiguration();

    private final StorageProperties properties = new StorageProperties();

    private final DefaultStorageConfigProvider configProvider = new DefaultStorageConfigProvider(properties);

    StorageAutoConfigurationTest() {
        configuration.setStorageProperties(properties);
    }

    @Test
    @DisplayName("零配置时自动启用 default 本地源")
    void autoLocalWhenZeroConfig() {
        assertThat(platforms()).containsExactly("default");
    }

    @Test
    @DisplayName("auto-local-enabled=false 时保持无源")
    void autoLocalCanBeDisabled() {
        properties.setAutoLocalEnabled(false);

        assertThat(platforms()).isEmpty();
    }

    @Test
    @DisplayName("显式配置本地源时不追加默认源")
    void explicitLocalWins() {
        LocalConfig config = new LocalConfig();
        config.setPlatform("files");
        config.setBasePath("/tmp/files");
        properties.setLocal(List.of(config));

        assertThat(platforms()).containsExactly("files");
    }

    private List<String> platforms() {
        return configuration.localStorageRegistrar(configProvider).strategies().stream()
            .map(StorageStrategy::platform)
            .toList();
    }
}

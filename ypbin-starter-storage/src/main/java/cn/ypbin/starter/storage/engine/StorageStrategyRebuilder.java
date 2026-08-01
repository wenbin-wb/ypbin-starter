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

import cn.ypbin.starter.storage.strategy.StorageStrategy;
import java.util.ArrayList;
import java.util.List;

/**
 * 存储源重建器。
 *
 * <p>后台修改存储源配置（{@link StorageConfigProvider} 背后的数据源）后调用 {@link #rebuild()}，
 * 重新收集所有 {@link StorageStrategyRegistrar} 贡献的策略并原子刷新 {@link StorageRouter}，
 * 新增/修改/删除的存储源即时生效，无需重启。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class StorageStrategyRebuilder {

    private final StorageRouter router;
    private final List<StorageStrategyRegistrar> registrars;
    private final StorageConfigProvider configProvider;

    public StorageStrategyRebuilder(StorageRouter router, List<StorageStrategyRegistrar> registrars,
        StorageConfigProvider configProvider) {
        this.router = router;
        this.registrars = registrars;
        this.configProvider = configProvider;
    }

    /**
     * 依据当前配置重建全部存储源并刷新路由。
     */
    public void rebuild() {
        List<StorageStrategy> all = new ArrayList<>();
        for (StorageStrategyRegistrar registrar : registrars) {
            all.addAll(registrar.strategies());
        }
        router.rebuild(all, configProvider.getDefaultPlatform());
    }
}

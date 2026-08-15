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

import cn.ypbin.starter.storage.exception.StorageException;
import cn.ypbin.starter.storage.strategy.StorageStrategy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 存储策略路由器。
 *
 * <p>以 platform 为键维护所有存储源，按键路由；支持运行时动态增删存储源。
 * 相比引入装饰器管理器 + 事件机制，这里采用最简的并发 Map，覆盖绝大多数场景，
 * 需要拦截 / 增强时由业务方包装 {@link StorageStrategy} 后再注册即可。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class StorageRouter {

    private final Map<String, StorageStrategy> strategies = new ConcurrentHashMap<>();

    private volatile String defaultPlatform;

    public StorageRouter(List<StorageStrategy> initialStrategies, String defaultPlatform) {
        for (StorageStrategy strategy : initialStrategies) {
            register(strategy);
        }
        this.defaultPlatform = defaultPlatform;
    }

    /**
     * 注册（或替换）一个存储源。
     *
     * @param strategy 存储策略
     */
    public void register(StorageStrategy strategy) {
        strategies.put(strategy.platform(), strategy);
    }

    /**
     * 全量刷新存储源（用于后台改配置后重建）：以传入集合为准替换全部存储源，移除已不存在的源。
     *
     * @param newStrategies   新的存储策略集合
     * @param defaultPlatform 新的默认平台，可空（空则保留原默认）
     */
    public synchronized void rebuild(List<StorageStrategy> newStrategies, String defaultPlatform) {
        Map<String, StorageStrategy> next = new ConcurrentHashMap<>();
        for (StorageStrategy strategy : newStrategies) {
            next.put(strategy.platform(), strategy);
        }
        strategies.keySet().retainAll(next.keySet());
        strategies.putAll(next);
        if (defaultPlatform != null && !defaultPlatform.isBlank()) {
            this.defaultPlatform = defaultPlatform;
        } else if ((this.defaultPlatform == null || !strategies.containsKey(this.defaultPlatform))
            && !newStrategies.isEmpty()) {
            this.defaultPlatform = newStrategies.getFirst().platform();
        }
    }

    /**
     * 注销一个存储源。
     *
     * @param platform 平台标识
     */
    public void unregister(String platform) {
        strategies.remove(platform);
    }

    /**
     * 设置默认平台。
     *
     * @param platform 平台标识
     */
    public void setDefaultPlatform(String platform) {
        this.defaultPlatform = platform;
    }

    /**
     * 按平台标识路由，为空时使用默认平台。
     *
     * @param platform 平台标识，可空
     * @return 存储策略
     */
    public StorageStrategy route(String platform) {
        String key = (platform != null && !platform.isBlank()) ? platform : defaultPlatform;
        if (key == null || key.isBlank()) {
            throw new StorageException("未指定存储平台且未配置默认平台");
        }
        StorageStrategy strategy = strategies.get(key);
        if (strategy == null) {
            throw new StorageException("存储平台不存在：" + key);
        }
        return strategy;
    }

    /**
     * 已注册的平台标识列表。
     *
     * @return 平台标识列表
     */
    public List<String> platforms() {
        return strategies.values().stream()
            .map(StorageStrategy::platform)
            .collect(Collectors.toList());
    }
}

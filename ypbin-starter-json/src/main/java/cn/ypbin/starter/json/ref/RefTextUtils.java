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
package cn.ypbin.starter.json.ref;

import java.util.Collection;

/**
 * 引用翻译静态门面。
 *
 * <p>供 {@link RefTextSerializer} 及非 Spring 托管场景静态调用。由自动配置在启动时通过 {@link #bind}
 * 注入 {@link RefTextManager}；未接入引用翻译（无 {@link RefTextProvider}）时，翻译安全退化为返回
 * {@code null}，不影响序列化。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public final class RefTextUtils {

    private static volatile RefTextManager manager;

    private RefTextUtils() {
    }

    /**
     * 绑定引用翻译管理器（由自动配置调用）。
     *
     * @param refTextManager 管理器
     */
    public static void bind(RefTextManager refTextManager) {
        manager = refTextManager;
    }

    /**
     * 是否已接入引用翻译能力。
     *
     * @return 是否就绪
     */
    public static boolean isReady() {
        return manager != null;
    }

    /**
     * 翻译单个引用 ID 为名称；未接入时返回 {@code null}。
     *
     * @param type 引用类型
     * @param id   引用 ID
     * @return 名称
     */
    public static String translate(String type, Object id) {
        return manager == null ? null : manager.translate(type, id);
    }

    /**
     * 批量预加载翻译到缓存；未接入时空操作。
     *
     * @param type 引用类型
     * @param ids  引用 ID 集合
     */
    public static void preload(String type, Collection<Object> ids) {
        if (manager != null) {
            manager.preload(type, ids);
        }
    }

    /**
     * 刷新全部翻译缓存。
     */
    public static void refresh() {
        if (manager != null) {
            manager.refresh();
        }
    }

    /**
     * 刷新指定类型翻译缓存。
     *
     * @param type 引用类型
     */
    public static void refresh(String type) {
        if (manager != null) {
            manager.refresh(type);
        }
    }
}

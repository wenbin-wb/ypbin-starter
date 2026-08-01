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
package cn.ypbin.starter.json.dict;

import java.util.List;

/**
 * 字典静态门面。
 *
 * <p>供 {@link DictTextSerializer} 及非 Spring 托管场景静态调用。由自动配置在启动时通过
 * {@link #bind(DictCache)} 注入 {@link DictCache}；未接入字典（无 {@link DictProvider}）时，
 * 翻译方法安全退化为返回原值，不影响序列化。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public final class DictUtils {

    private static volatile DictCache cache;

    private DictUtils() {
    }

    /**
     * 绑定字典缓存（由自动配置调用）。
     *
     * @param dictCache 字典缓存
     */
    public static void bind(DictCache dictCache) {
        cache = dictCache;
    }

    /**
     * 是否已接入字典能力。
     *
     * @return 是否就绪
     */
    public static boolean isReady() {
        return cache != null;
    }

    /**
     * 翻译字典值为展示文本；未接入字典或无匹配时返回原值。
     *
     * @param dictType 字典类型
     * @param value    字典值
     * @return 展示文本
     */
    public static String translate(String dictType, String value) {
        return cache == null ? value : cache.translate(dictType, value);
    }

    /**
     * 获取字典项列表；未接入字典时返回空列表。
     *
     * @param dictType 字典类型
     * @return 字典项列表
     */
    public static List<DictItem> getItems(String dictType) {
        return cache == null ? List.of() : cache.getItems(dictType);
    }

    /**
     * 刷新全部字典缓存。
     */
    public static void refresh() {
        if (cache != null) {
            cache.refresh();
        }
    }

    /**
     * 刷新指定字典类型缓存。
     *
     * @param dictType 字典类型
     */
    public static void refresh(String dictType) {
        if (cache != null) {
            cache.refresh(dictType);
        }
    }
}

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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 字典缓存。
 *
 * <p>包装 {@link DictProvider}，按字典类型缓存字典项与 value→label 映射，避免每次翻译都回源。
 * 字典数据量小、变更少，用本地并发 Map 即可；业务方在字典维护后调用 {@link #refresh()} 或
 * {@link #refresh(String)} 清缓存即可即时生效，无需重启。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class DictCache {

    private final DictProvider provider;

    /** dictType -> 字典项列表 */
    private final Map<String, List<DictItem>> itemsCache = new ConcurrentHashMap<>();
    /** dictType -> (value -> label) */
    private final Map<String, Map<String, String>> labelCache = new ConcurrentHashMap<>();

    public DictCache(DictProvider provider) {
        this.provider = provider;
    }

    /**
     * 获取字典项列表（带缓存）。
     *
     * @param dictType 字典类型
     * @return 字典项列表
     */
    public List<DictItem> getItems(String dictType) {
        if (dictType == null || dictType.isBlank()) {
            return List.of();
        }
        return itemsCache.computeIfAbsent(dictType, this::loadItems);
    }

    /**
     * 将字典值翻译为展示文本。
     *
     * @param dictType 字典类型
     * @param value    字典值（code）
     * @return 展示文本；无匹配时返回原值
     */
    public String translate(String dictType, String value) {
        if (dictType == null || dictType.isBlank() || value == null) {
            return value;
        }
        Map<String, String> labels = labelCache.computeIfAbsent(dictType, this::loadLabels);
        return labels.getOrDefault(value, value);
    }

    /**
     * 清空全部字典缓存。
     */
    public void refresh() {
        itemsCache.clear();
        labelCache.clear();
    }

    /**
     * 清空指定字典类型的缓存。
     *
     * @param dictType 字典类型
     */
    public void refresh(String dictType) {
        if (dictType != null) {
            itemsCache.remove(dictType);
            labelCache.remove(dictType);
        }
    }

    private List<DictItem> loadItems(String dictType) {
        List<DictItem> items = provider.getItems(dictType);
        return items == null ? List.of() : List.copyOf(items);
    }

    private Map<String, String> loadLabels(String dictType) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (DictItem item : getItems(dictType)) {
            if (item.getValue() != null) {
                labels.put(item.getValue(), item.getLabel());
            }
        }
        return Collections.unmodifiableMap(labels);
    }
}

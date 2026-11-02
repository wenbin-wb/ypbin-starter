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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 引用翻译管理器。
 *
 * <p>编排 {@link RefTextProvider}（按 type 索引）+ {@link RefTextCache}，是规避列表翻译 N+1 的核心：</p>
 * <ul>
 *     <li>{@link #translate}：单值翻译，优先命中缓存；</li>
 *     <li>{@link #preload}：<b>批量预加载</b>——一次收集一批 ID，缓存未命中的部分按 type 合并成
 *         <b>一次批量查询</b>（{@code getNames(ids)}），结果回填缓存。列表序列化前调用它，序列化时全部命中缓存，
 *         无逐行回源；</li>
 *     <li>{@link #refresh}：数据变更后清缓存，下次翻译即时生效。</li>
 * </ul>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class RefTextManager {

    private static final Logger log = LoggerFactory.getLogger(RefTextManager.class);

    private final Map<String, RefTextProvider> providers = new HashMap<>();
    private final RefTextCache cache;

    public RefTextManager(List<RefTextProvider> providerList, RefTextCache cache) {
        this.cache = cache;
        for (RefTextProvider provider : providerList) {
            if (provider.type() != null) {
                providers.put(provider.type(), provider);
            }
        }
    }

    /**
     * 翻译单个引用 ID 为名称。命中缓存直接返回；未命中回源并回填。
     *
     * @param type 引用类型
     * @param id   引用 ID
     * @return 名称；无 provider 或查不到时返回 {@code null}
     */
    @Nullable
    public String translate(String type, Object id) {
        if (type == null || id == null) {
            return null;
        }
        String cached = cache.get(type, id);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        RefTextProvider provider = providers.get(type);
        if (provider == null) {
            return null;
        }
        Optional<Map<Object, String>> names = safeGetNames(provider, List.of(id));
        if (names.isEmpty()) {
            // 回源失败：**不写缓存**。否则一次 DB/Feign 抖动会被当成「查无此项」缓存整个 TTL，
            // 数据源恢复后仍持续返回原始 ID。
            return null;
        }
        String name = names.get().get(id);
        cache.put(type, id, name);
        return name;
    }

    /**
     * 批量预加载一组 ID 的翻译到缓存。缓存已命中的跳过，未命中的按类型合并为一次批量查询。
     *
     * <p>列表/分页数据序列化前调用（由 {@link RefTextResolver} 自动触发），可把 N 行 M 类型的翻译回源
     * 压缩为最多 M 次批量查询。</p>
     *
     * @param type 引用类型
     * @param ids  引用 ID 集合
     */
    public void preload(String type, Collection<Object> ids) {
        if (type == null || ids == null || ids.isEmpty()) {
            return;
        }
        RefTextProvider provider = providers.get(type);
        if (provider == null) {
            return;
        }
        // 只查缓存未命中的 ID，去重
        Set<Object> missing = new LinkedHashSet<>();
        for (Object id : ids) {
            if (id != null && !cache.contains(type, id)) {
                missing.add(id);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        Optional<Map<Object, String>> names = safeGetNames(provider, missing);
        if (names.isEmpty()) {
            // 回源失败：整批都不回填（含空值哨兵）。否则一次失败就把整批 ID 当"查无此项"
            // 缓存 TTL 默认 300s——故障恢复后仍需 5 分钟才恢复展示。
            return;
        }
        // 回填命中项 + 未查到的写空值哨兵，防后续穿透
        Map<Object, String> resolved = names.get();
        for (Object id : missing) {
            cache.put(type, id, resolved.get(id));
        }
    }

    /**
     * 是否支持某引用类型（存在对应 provider）。
     *
     * @param type 引用类型
     * @return 是否支持
     */
    public boolean supports(String type) {
        return providers.containsKey(type);
    }

    /**
     * 清空全部翻译缓存。
     */
    public void refresh() {
        cache.clear();
    }

    /**
     * 清空指定类型翻译缓存。
     *
     * @param type 引用类型
     */
    public void refresh(String type) {
        cache.clear(type);
    }

    /**
     * 安全回源。
     *
     * @param provider 数据源
     * @param ids      待查 ID
     * @return 名称映射（provider 返回 null 时按空映射处理）；<strong>回源失败返回空 Optional</strong>，
     *         调用方据此跳过缓存回填——失败与「确实查无此项」必须区分
     */
    private Optional<Map<Object, String>> safeGetNames(RefTextProvider provider, Collection<Object> ids) {
        try {
            Map<Object, String> names = provider.getNames(new ArrayList<>(ids));
            return Optional.of(names == null ? Map.of() : names);
        } catch (Exception e) {
            log.warn("[ypbin-starter] 引用翻译回源失败 type={}，本次不回填缓存，相关引用文本回退为原始 ID: {}",
                provider.type(), e.getMessage(), e);
            return Optional.empty();
        }
    }
}

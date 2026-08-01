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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 引用翻译本地缓存。
 *
 * <p>按 {@code type::id} 缓存翻译名称，带 TTL 过期与容量上限：</p>
 * <ul>
 *     <li><b>命中即返回</b>：列表中重复 ID（如多行同一创建人）只回源一次，其余走缓存；</li>
 *     <li><b>空值哨兵</b>：查无结果也缓存空串一小段时间，防止对不存在 ID 反复穿透回源；</li>
 *     <li><b>容量上限 + 惰性清理</b>：超过 maxSize 触发清理过期项，仍超则不再写入，避免内存无限增长。</li>
 * </ul>
 *
 * <p>字典/引用数据变更后由业务调用 {@link RefTextManager#refresh}/{@link RefTextManager#refresh(String)}
 * 清缓存即可即时生效。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class RefTextCache {

    /** 缓存值 + 过期时间戳 */
    private record Entry(String name, long expireAt) {
    }

    private static final long CLEANUP_INTERVAL_MILLIS = 30_000L;

    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong(System.currentTimeMillis());

    private final long ttlMillis;
    private final int maxSize;

    public RefTextCache(long ttlMillis, int maxSize) {
        this.ttlMillis = ttlMillis;
        this.maxSize = maxSize;
    }

    /**
     * 取缓存名称。
     *
     * @param type 引用类型
     * @param id   引用 ID
     * @return 命中返回名称（含空值哨兵的空串）；未命中返回 {@code null}
     */
    public String get(String type, Object id) {
        Entry entry = cache.get(key(type, id));
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() >= entry.expireAt()) {
            cache.remove(key(type, id));
            return null;
        }
        return entry.name();
    }

    /**
     * 是否已缓存（含空值哨兵）。
     *
     * @param type 引用类型
     * @param id   引用 ID
     * @return 是否命中
     */
    public boolean contains(String type, Object id) {
        return get(type, id) != null;
    }

    /**
     * 写入缓存。
     *
     * @param type 引用类型
     * @param id   引用 ID
     * @param name 名称（查无结果时传空串作哨兵）
     */
    public void put(String type, Object id, String name) {
        long now = System.currentTimeMillis();
        cleanupIfNeeded(now);
        if (cache.size() >= maxSize && !cache.containsKey(key(type, id))) {
            // 已达上限：先尝试清理过期项，仍满则放弃写入（保护内存）
            cache.entrySet().removeIf(e -> now >= e.getValue().expireAt());
            if (cache.size() >= maxSize) {
                return;
            }
        }
        cache.put(key(type, id), new Entry(name == null ? "" : name, now + ttlMillis));
    }

    /**
     * 清空全部缓存。
     */
    public void clear() {
        cache.clear();
    }

    /**
     * 清空指定类型缓存。
     *
     * @param type 引用类型
     */
    public void clear(String type) {
        String prefix = type + "::";
        cache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    private String key(String type, Object id) {
        return type + "::" + id;
    }

    private void cleanupIfNeeded(long now) {
        long last = lastCleanup.get();
        if (now - last < CLEANUP_INTERVAL_MILLIS) {
            return;
        }
        if (!lastCleanup.compareAndSet(last, now)) {
            return;
        }
        cache.entrySet().removeIf(e -> now >= e.getValue().expireAt());
    }
}

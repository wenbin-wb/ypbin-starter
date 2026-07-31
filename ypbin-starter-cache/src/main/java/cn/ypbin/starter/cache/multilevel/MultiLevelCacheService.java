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
package cn.ypbin.starter.cache.multilevel;

import cn.ypbin.starter.cache.core.CacheService;
import com.github.benmanes.caffeine.cache.Cache;
import java.time.Duration;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * 多级缓存实现：L1 本地（Caffeine）+ L2 分布式（委托底层 {@link CacheService}，通常为 Redis）。
 *
 * <p>读路径：先查 L1，命中直接返回；未命中查 L2，命中则回填 L1；仍未命中经 L2 的 {@link #getOrLoad}
 * 回源（含防击穿/穿透/雪崩）后回填 L1。写/删路径：更新 L2 并失效本地 L1，同时通过
 * {@link CacheInvalidationPublisher} 广播失效，通知其它实例摘除各自 L1，保证多实例最终一致。</p>
 *
 * <p>L1 极大降低热点 key 的 Redis 往返；代价是本地缓存存在极短的不一致窗口（广播延迟内），
 * 适合读多写少、可容忍秒级不一致的场景。强一致数据不应启用多级缓存。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class MultiLevelCacheService implements CacheService {

    private final CacheService l2;
    private final Cache<String, Object> l1;
    private final CacheInvalidationPublisher invalidationPublisher;

    /** 空值哨兵：与 L2 一致，L1 也缓存空值防穿透 */
    private static final Object NULL_SENTINEL = new Object();

    public MultiLevelCacheService(CacheService l2, Cache<String, Object> l1,
            CacheInvalidationPublisher invalidationPublisher) {
        this.l2 = l2;
        this.l1 = l1;
        this.invalidationPublisher = invalidationPublisher;
    }

    @Override
    public void set(String key, Object value) {
        l2.set(key, value);
        invalidateLocalAndBroadcast(key);
    }

    @Override
    public void set(String key, Object value, Duration timeout) {
        l2.set(key, value, timeout);
        invalidateLocalAndBroadcast(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object local = l1.getIfPresent(key);
        if (local != null) {
            return local == NULL_SENTINEL ? null : (T) local;
        }
        T value = l2.get(key, type);
        if (value != null) {
            l1.put(key, value);
        }
        return value;
    }

    @Override
    public boolean delete(String key) {
        boolean removed = l2.delete(key);
        invalidateLocalAndBroadcast(key);
        return removed;
    }

    @Override
    public long delete(Collection<String> keys) {
        long count = l2.delete(keys);
        keys.forEach(this::invalidateLocalAndBroadcast);
        return count;
    }

    @Override
    public boolean exists(String key) {
        Object local = l1.getIfPresent(key);
        if (local != null) {
            // 本地空值哨兵不算"存在有效业务数据"，与 get 返回 null 的语义保持一致
            return local != NULL_SENTINEL;
        }
        return l2.exists(key);
    }

    @Override
    public boolean expire(String key, Duration timeout) {
        return l2.expire(key, timeout);
    }

    @Override
    public long increment(String key, long delta) {
        // 计数类语义强依赖单一数据源，直接走 L2 并失效 L1，避免本地缓存导致计数不一致
        long value = l2.increment(key, delta);
        invalidateLocalAndBroadcast(key);
        return value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(String key, Class<T> type, Supplier<T> loader, Duration ttl) {
        Object local = l1.getIfPresent(key);
        if (local != null) {
            return local == NULL_SENTINEL ? null : (T) local;
        }
        // L2 的 getOrLoad 已含防击穿/穿透/雪崩，回源结果回填 L1
        T value = l2.getOrLoad(key, type, loader, ttl);
        l1.put(key, value == null ? NULL_SENTINEL : value);
        return value;
    }

    /** 失效本地 L1 并广播，通知其它实例 */
    private void invalidateLocalAndBroadcast(String key) {
        l1.invalidate(key);
        if (invalidationPublisher != null) {
            invalidationPublisher.publish(key);
        }
    }

    /** 供订阅方回调：仅失效本地 L1，不再广播（避免广播风暴） */
    public void invalidateLocalOnly(String key) {
        l1.invalidate(key);
    }
}

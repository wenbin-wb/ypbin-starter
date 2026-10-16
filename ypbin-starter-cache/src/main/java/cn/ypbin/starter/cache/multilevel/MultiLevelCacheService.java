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
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * 多级缓存实现：L1 本地（Caffeine）+ L2 分布式（委托底层 {@link CacheService}，通常为 Redis）。
 *
 * <p>读路径：{@link #get} 先查 L1，命中直接返回；未命中查 L2，命中回填 L1，<strong>L2 也未命中则回填
 * 空值哨兵</strong>（与 {@link #getOrLoad} 同一口径，防穿透；L1 空值哨兵的陈旧窗口边界见
 * {@link #get} 方法注释）。{@link #getOrLoad} 同样先查 L1，未命中经 L2 回源（含防击穿/穿透/雪崩）
 * 后回填 L1。写/删路径：更新 L2 并失效本地 L1，同时通过 {@link CacheInvalidationPublisher}
 * 广播失效，通知其它实例摘除各自 L1，保证多实例最终一致。</p>
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
    @Nullable
    private final CacheInvalidationPublisher invalidationPublisher;
    private final CacheMetricsRecorder metrics;

    /** 空值哨兵：与 L2 一致，L1 也缓存空值防穿透 */
    private static final Object NULL_SENTINEL = new Object();

    public MultiLevelCacheService(CacheService l2, Cache<String, Object> l1,
            @Nullable CacheInvalidationPublisher invalidationPublisher) {
        this(l2, l1, invalidationPublisher, CacheMetricsRecorder.NOOP);
    }

    /**
     * 带运行时指标记录的构造器（公开重载，既有三参构造器保持兼容）。
     *
     * @param l2                    L2 缓存（非空）
     * @param l1                    L1 本地缓存（非空）
     * @param invalidationPublisher 失效广播器，可为 {@code null}（不广播）
     * @param metrics               指标记录器（非空；未接入 Micrometer 时传
     *                              {@link CacheMetricsRecorder#NOOP}）
     */
    public MultiLevelCacheService(CacheService l2, Cache<String, Object> l1,
            @Nullable CacheInvalidationPublisher invalidationPublisher, CacheMetricsRecorder metrics) {
        this.l2 = Objects.requireNonNull(l2, "l2 不能为空");
        this.l1 = Objects.requireNonNull(l1, "l1 不能为空");
        this.invalidationPublisher = invalidationPublisher;
        this.metrics = Objects.requireNonNull(metrics, "metrics 不能为空");
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
    public boolean setIfAbsent(String key, Object value, Duration timeout) {
        boolean stored = l2.setIfAbsent(key, value, timeout);
        invalidateLocalAndBroadcast(key);
        return stored;
    }

    /**
     * 读取缓存：L1 命中直接返回（空值哨兵返回 {@code null}）；L1 未命中查 L2，并<strong>无论是否命中
     * 都回填 L1</strong>——L2 也没有时回填空值哨兵，防止不存在的 key 每次都穿透 L1 打到 L2。
     *
     * <p><strong>空值哨兵的陈旧窗口（部署/配置边界）</strong>：L1 里空值哨兵的存活时间等于
     * {@code l1Cache} 的 Caffeine {@code expireAfterWrite}（见
     * {@code MultiLevelCacheAutoConfiguration#l1Cache}，默认
     * {@code MultiLevelCacheProperties#localExpireSeconds} = 300s）。窗口内若数据在 L2 出现，
     * 只有满足下列条件之一才会被摘除：</p>
     * <ul>
     *   <li>写入方经由本类写路径（{@code set}/{@code setIfAbsent}/{@code delete}/
     *       {@code compareAndDelete}/{@code increment}/{@code expire}）→ 本机 {@code l1.invalidate}
     *       并广播；其它实例经 {@link #invalidateLocalOnly(String)} 摘除；</li>
     *   <li>哨兵自然到期（最长 {@code localExpireSeconds}）。</li>
     * </ul>
     * <p>因此<strong>绕过本类直接写 L2（如外部系统、另一进程直写同一 Redis key）时，本机最长
     * {@code localExpireSeconds} 内仍会返回 {@code null}</strong>。把 {@code localExpireSeconds}
     * 配置得远大于可接受的陈旧窗口会放大该影响；强一致场景不应启用多级缓存（类注释已声明）。</p>
     *
     * @param key  键
     * @param type 期望类型
     * @param <T>  泛型
     * @return 值，不存在时为 {@code null}
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T get(String key, Class<T> type) {
        Object local = l1.getIfPresent(key);
        if (local != null) {
            metrics.l1Hit();
            if (local == NULL_SENTINEL) {
                // 被空值哨兵拦截：本地命中但结果为 null，即一次「防穿透阻断」
                metrics.nullSentinelHit();
                return null;
            }
            return (T) local;
        }
        metrics.l1Miss();
        T value = l2.get(key, type);
        if (value == null) {
            metrics.l2Miss();
        } else {
            metrics.l2Hit();
        }
        // 与 getOrLoad 同一口径：L2 也没有时回填空值哨兵，否则不存在的 key 每次都穿透 L1 打到 L2
        l1.put(key, value == null ? NULL_SENTINEL : value);
        return value;
    }

    @Override
    public boolean delete(String key) {
        boolean removed = l2.delete(key);
        invalidateLocalAndBroadcast(key);
        return removed;
    }

    @Override
    public boolean compareAndDelete(String key, Object expected) {
        boolean removed = l2.compareAndDelete(key, expected);
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
        boolean expired = l2.expire(key, timeout);
        if (expired) {
            // 重设 TTL 等同一次写操作：失效本地 L1 并广播，避免各实例按原 L1 TTL 继续返回旧值
            invalidateLocalAndBroadcast(key);
        } else {
            // key 在 L2 不存在：无需广播其它实例，仅清理本机 L1 可能残留的过期值
            l1.invalidate(key);
        }
        return expired;
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
    public <T> @Nullable T getOrLoad(String key, Class<T> type, Supplier<T> loader, Duration ttl) {
        Object local = l1.getIfPresent(key);
        if (local != null) {
            metrics.l1Hit();
            if (local == NULL_SENTINEL) {
                metrics.nullSentinelHit();
                return null;
            }
            return (T) local;
        }
        metrics.l1Miss();
        // L2 的 getOrLoad 已含防击穿/穿透/雪崩，回源结果回填 L1
        T value = l2.getOrLoad(key, type, loader, ttl);
        metrics.load();
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

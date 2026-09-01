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
package cn.ypbin.starter.cache.core;

import java.time.Duration;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * 缓存能力契约。
 *
 * <p>面向业务提供与具体实现无关的缓存操作。当前默认基于 Redis 实现，
 * 后续新增多级缓存 / 本地缓存等实现时，业务代码无需变更。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public interface CacheService {

    /**
     * 写入缓存（永不过期）。
     *
     * @param key   键
     * @param value 值
     */
    void set(String key, Object value);

    /**
     * 写入缓存并设置过期时间。
     *
     * @param key     键
     * @param value   值
     * @param timeout 过期时长
     */
    void set(String key, Object value, Duration timeout);

    /**
     * 仅当键不存在时原子写入缓存并设置过期时间。
     *
     * @param key     键
     * @param value   值
     * @param timeout 过期时长
     * @return 是否写入成功
     */
    boolean setIfAbsent(String key, Object value, Duration timeout);

    /**
     * 读取缓存。
     *
     * @param key  键
     * @param type 期望类型
     * @param <T>  泛型
     * @return 值，不存在时为 {@code null}
     */
    <T> T get(String key, Class<T> type);

    /**
     * 删除单个键。
     *
     * @param key 键
     * @return 是否删除成功
     */
    boolean delete(String key);

    /**
     * 仅当缓存值与期望值相等时原子删除。
     *
     * @param key      键
     * @param expected 期望值
     * @return 是否匹配并删除成功
     */
    boolean compareAndDelete(String key, Object expected);

    /**
     * 批量删除。
     *
     * @param keys 键集合
     * @return 删除数量
     */
    long delete(Collection<String> keys);

    /**
     * 判断键是否存在。
     *
     * @param key 键
     * @return 是否存在
     */
    boolean exists(String key);

    /**
     * 设置过期时间。
     *
     * @param key     键
     * @param timeout 过期时长
     * @return 是否设置成功
     */
    boolean expire(String key, Duration timeout);

    /**
     * 原子自增。
     *
     * @param key   键
     * @param delta 增量
     * @return 自增后的值
     */
    long increment(String key, long delta);

    /**
     * 读取缓存，未命中则回源加载并回填（缓存旁路模式）。
     *
     * <p>实现应内置三重保护：防击穿（回源时加锁/单飞，避免热点 key 过期瞬间大量请求打穿到数据源）、
     * 防穿透（回源结果为 {@code null} 时缓存空值标记短时，避免不存在的 key 反复打到数据源）、
     * 防雪崩（TTL 叠加随机扰动，避免大量 key 同一时刻集中过期）。</p>
     *
     * <p>ttl 传 {@code null} 表示永久缓存（不过期）：适用于「主动失效」模式——数据变更由业务方
     * 显式 {@link #delete(String)} 清理，缓存一致性由主动失效保证，无需时间兜底。</p>
     *
     * @param key    键
     * @param type   期望类型
     * @param loader 回源加载函数（缓存未命中时调用；返回 {@code null} 表示数据源无此数据）
     * @param ttl    缓存过期时长，{@code null} 表示永久（靠主动失效清理）
     * @param <T>    泛型
     * @return 缓存值或回源结果，数据源也无数据时返回 {@code null}
     */
    <T> T getOrLoad(String key, Class<T> type, Supplier<T> loader, Duration ttl);
}

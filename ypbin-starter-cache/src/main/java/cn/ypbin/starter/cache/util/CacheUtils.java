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
package cn.ypbin.starter.cache.util;

import cn.ypbin.starter.cache.core.CacheService;
import cn.ypbin.starter.core.util.SpringUtils;
import java.time.Duration;
import java.util.Collection;
import java.util.function.Supplier;

/**
 * 缓存静态工具。
 *
 * <p>面向非 Spring 托管对象（静态方法、工具类等）提供便捷缓存访问，内部委托容器中的
 * {@link CacheService} Bean。首次调用时经 {@link SpringUtils} 懒获取并缓存该 Bean 引用。</p>
 *
 * <p>Spring 托管的组件应优先直接注入 {@link CacheService}，语义更清晰、更易测试；本工具仅用于
 * 无法依赖注入的场景。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public final class CacheUtils {

    private static volatile CacheService cacheService;

    private CacheUtils() {
    }

    /**
     * 懒获取容器中的 {@link CacheService} Bean（双重检查，线程安全）。
     *
     * @return 缓存服务实例
     */
    private static CacheService cacheService() {
        if (cacheService == null) {
            synchronized (CacheUtils.class) {
                if (cacheService == null) {
                    cacheService = SpringUtils.getBean(CacheService.class);
                }
            }
        }
        return cacheService;
    }

    /**
     * 写入缓存（永不过期）。
     *
     * @param key   键
     * @param value 值
     */
    public static void set(String key, Object value) {
        cacheService().set(key, value);
    }

    /**
     * 写入缓存并设置过期时间。
     *
     * @param key     键
     * @param value   值
     * @param timeout 过期时长
     */
    public static void set(String key, Object value, Duration timeout) {
        cacheService().set(key, value, timeout);
    }

    /**
     * 读取缓存。
     *
     * @param key  键
     * @param type 期望类型
     * @param <T>  泛型
     * @return 值，不存在时为 {@code null}
     */
    public static <T> T get(String key, Class<T> type) {
        return cacheService().get(key, type);
    }

    /**
     * 删除单个键。
     *
     * @param key 键
     * @return 是否删除成功
     */
    public static boolean delete(String key) {
        return cacheService().delete(key);
    }

    /**
     * 批量删除。
     *
     * @param keys 键集合
     * @return 删除数量
     */
    public static long delete(Collection<String> keys) {
        return cacheService().delete(keys);
    }

    /**
     * 判断键是否存在。
     *
     * @param key 键
     * @return 是否存在
     */
    public static boolean exists(String key) {
        return cacheService().exists(key);
    }

    /**
     * 设置过期时间。
     *
     * @param key     键
     * @param timeout 过期时长
     * @return 是否设置成功
     */
    public static boolean expire(String key, Duration timeout) {
        return cacheService().expire(key, timeout);
    }

    /**
     * 原子自增。
     *
     * @param key   键
     * @param delta 增量
     * @return 自增后的值
     */
    public static long increment(String key, long delta) {
        return cacheService().increment(key, delta);
    }

    /**
     * 读取缓存，未命中则回源加载并回填（内置防击穿/穿透/雪崩，见 {@link CacheService#getOrLoad}）。
     *
     * @param key    键
     * @param type   期望类型
     * @param loader 回源加载函数
     * @param ttl    过期时长
     * @param <T>    泛型
     * @return 缓存值或回源结果
     */
    public static <T> T getOrLoad(String key, Class<T> type, Supplier<T> loader, Duration ttl) {
        return cacheService().getOrLoad(key, type, loader, ttl);
    }
}

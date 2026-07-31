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

import cn.ypbin.starter.core.util.SpringUtils;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Redis 全能力静态工具。
 *
 * <p>{@link CacheUtils} 面向「与实现无关」的通用缓存契约；本类则直接封装 {@link RedisTemplate} 的
 * Redis 专属数据结构操作——key 通用、string、hash、list、set、zset，覆盖业务高频场景，
 * 免去自行操作 {@code opsForXxx} 的样板代码。</p>
 *
 * <p>内部经 {@link SpringUtils} 懒获取容器中名为 {@code redisTemplate} 的
 * {@code RedisTemplate<String, Object>}（由 cache 模块默认装配）。非 Spring 托管场景可用；
 * Spring 组件仍建议直接注入 RedisTemplate。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public final class RedisUtils {

    @SuppressWarnings("unchecked")
    private static volatile RedisTemplate<String, Object> template;

    private RedisUtils() {
    }

    /**
     * 懒获取 {@code RedisTemplate<String, Object>}（双重检查，线程安全）。
     *
     * @return Redis 模板
     */
    @SuppressWarnings("unchecked")
    private static RedisTemplate<String, Object> redis() {
        if (template == null) {
            synchronized (RedisUtils.class) {
                if (template == null) {
                    template = SpringUtils.getBean("redisTemplate", RedisTemplate.class);
                }
            }
        }
        return template;
    }

    // ------------------------------------------------------------------ key 通用

    /**
     * 判断 key 是否存在。
     *
     * @param key 键
     * @return 是否存在
     */
    public static boolean hasKey(String key) {
        return Boolean.TRUE.equals(redis().hasKey(key));
    }

    /**
     * 删除单个 key。
     *
     * @param key 键
     * @return 是否删除成功
     */
    public static boolean delete(String key) {
        return Boolean.TRUE.equals(redis().delete(key));
    }

    /**
     * 批量删除 key。
     *
     * @param keys 键集合
     * @return 删除数量
     */
    public static long delete(Collection<String> keys) {
        Long n = redis().delete(keys);
        return n == null ? 0L : n;
    }

    /**
     * 设置过期时间。
     *
     * @param key     键
     * @param timeout 过期时长
     * @return 是否成功
     */
    public static boolean expire(String key, Duration timeout) {
        return Boolean.TRUE.equals(redis().expire(key, timeout));
    }

    /**
     * 获取剩余过期时间（秒）；-1 永不过期，-2 不存在。
     *
     * @param key 键
     * @return 剩余秒数
     */
    public static long getExpire(String key) {
        Long t = redis().getExpire(key, TimeUnit.SECONDS);
        return t == null ? -2L : t;
    }

    /**
     * 移除 key 的过期时间（转为持久）。
     *
     * @param key 键
     * @return 是否成功
     */
    public static boolean persist(String key) {
        return Boolean.TRUE.equals(redis().persist(key));
    }

    /**
     * 按模式匹配 key（生产环境大数据量慎用，建议用 scan 替代）。
     *
     * @param pattern 匹配模式，如 {@code user:*}
     * @return 匹配的 key 集合
     */
    public static Set<String> keys(String pattern) {
        return redis().keys(pattern);
    }

    /**
     * 重命名 key。
     *
     * @param oldKey 原 key
     * @param newKey 新 key
     */
    public static void rename(String oldKey, String newKey) {
        redis().rename(oldKey, newKey);
    }

    // ------------------------------------------------------------------ string (value)

    /**
     * 写入值（永不过期）。
     *
     * @param key   键
     * @param value 值
     */
    public static void set(String key, Object value) {
        redis().opsForValue().set(key, value);
    }

    /**
     * 写入值并设置过期时间。
     *
     * @param key     键
     * @param value   值
     * @param timeout 过期时长
     */
    public static void set(String key, Object value, Duration timeout) {
        redis().opsForValue().set(key, value, timeout);
    }

    /**
     * 仅当 key 不存在时写入（分布式锁常用），并设置过期时间。
     *
     * @param key     键
     * @param value   值
     * @param timeout 过期时长
     * @return 是否写入成功（key 原本不存在）
     */
    public static boolean setIfAbsent(String key, Object value, Duration timeout) {
        return Boolean.TRUE.equals(redis().opsForValue().setIfAbsent(key, value, timeout));
    }

    /**
     * 读取值。
     *
     * @param key 键
     * @param <T> 类型
     * @return 值，不存在为 {@code null}
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(String key) {
        return (T) redis().opsForValue().get(key);
    }

    /**
     * 读取旧值并写入新值（原子）。
     *
     * @param key   键
     * @param value 新值
     * @param <T>   类型
     * @return 旧值
     */
    @SuppressWarnings("unchecked")
    public static <T> T getAndSet(String key, Object value) {
        return (T) redis().opsForValue().getAndSet(key, value);
    }

    /**
     * 批量写入。
     *
     * @param map 键值对
     */
    public static void multiSet(Map<String, Object> map) {
        redis().opsForValue().multiSet(map);
    }

    /**
     * 批量读取。
     *
     * @param keys 键集合
     * @return 值列表（顺序与入参一致，缺失为 null）
     */
    public static List<Object> multiGet(Collection<String> keys) {
        return redis().opsForValue().multiGet(keys);
    }

    /**
     * 原子自增。
     *
     * @param key   键
     * @param delta 增量
     * @return 自增后的值
     */
    public static long increment(String key, long delta) {
        Long v = redis().opsForValue().increment(key, delta);
        return v == null ? 0L : v;
    }

    /**
     * 原子自减。
     *
     * @param key   键
     * @param delta 减量
     * @return 自减后的值
     */
    public static long decrement(String key, long delta) {
        Long v = redis().opsForValue().decrement(key, delta);
        return v == null ? 0L : v;
    }

    // ------------------------------------------------------------------ hash

    /**
     * 写入 hash 字段。
     *
     * @param key      键
     * @param hashKey  字段名
     * @param value    字段值
     */
    public static void hSet(String key, String hashKey, Object value) {
        redis().opsForHash().put(key, hashKey, value);
    }

    /**
     * 批量写入 hash。
     *
     * @param key 键
     * @param map 字段-值映射
     */
    public static void hSetAll(String key, Map<String, Object> map) {
        redis().opsForHash().putAll(key, map);
    }

    /**
     * 读取 hash 字段。
     *
     * @param key     键
     * @param hashKey 字段名
     * @param <T>     类型
     * @return 字段值
     */
    @SuppressWarnings("unchecked")
    public static <T> T hGet(String key, String hashKey) {
        return (T) redis().opsForHash().get(key, hashKey);
    }

    /**
     * 读取整个 hash。
     *
     * @param key 键
     * @return 全部字段-值
     */
    public static Map<Object, Object> hGetAll(String key) {
        return redis().opsForHash().entries(key);
    }

    /**
     * 判断 hash 字段是否存在。
     *
     * @param key     键
     * @param hashKey 字段名
     * @return 是否存在
     */
    public static boolean hExists(String key, String hashKey) {
        return redis().opsForHash().hasKey(key, hashKey);
    }

    /**
     * 删除 hash 字段。
     *
     * @param key      键
     * @param hashKeys 字段名
     * @return 删除数量
     */
    public static long hDelete(String key, Object... hashKeys) {
        return redis().opsForHash().delete(key, hashKeys);
    }

    /**
     * hash 字段原子自增。
     *
     * @param key     键
     * @param hashKey 字段名
     * @param delta   增量
     * @return 自增后的值
     */
    public static long hIncrement(String key, String hashKey, long delta) {
        return redis().opsForHash().increment(key, hashKey, delta);
    }

    // ------------------------------------------------------------------ list

    /**
     * 从右侧压入元素（尾部追加）。
     *
     * @param key   键
     * @param value 元素
     * @return 操作后列表长度
     */
    public static long rPush(String key, Object value) {
        Long n = redis().opsForList().rightPush(key, value);
        return n == null ? 0L : n;
    }

    /**
     * 从左侧压入元素（头部插入）。
     *
     * @param key   键
     * @param value 元素
     * @return 操作后列表长度
     */
    public static long lPush(String key, Object value) {
        Long n = redis().opsForList().leftPush(key, value);
        return n == null ? 0L : n;
    }

    /**
     * 从左侧弹出元素。
     *
     * @param key 键
     * @param <T> 类型
     * @return 元素，空列表为 {@code null}
     */
    @SuppressWarnings("unchecked")
    public static <T> T lPop(String key) {
        return (T) redis().opsForList().leftPop(key);
    }

    /**
     * 从右侧弹出元素。
     *
     * @param key 键
     * @param <T> 类型
     * @return 元素，空列表为 {@code null}
     */
    @SuppressWarnings("unchecked")
    public static <T> T rPop(String key) {
        return (T) redis().opsForList().rightPop(key);
    }

    /**
     * 按范围读取列表元素。
     *
     * @param key   键
     * @param start 起始下标
     * @param end   结束下标（-1 表示末尾）
     * @return 元素列表
     */
    public static List<Object> lRange(String key, long start, long end) {
        return redis().opsForList().range(key, start, end);
    }

    /**
     * 列表长度。
     *
     * @param key 键
     * @return 长度
     */
    public static long lSize(String key) {
        Long n = redis().opsForList().size(key);
        return n == null ? 0L : n;
    }

    // ------------------------------------------------------------------ set

    /**
     * 向 set 添加元素。
     *
     * @param key    键
     * @param values 元素
     * @return 新增数量
     */
    public static long sAdd(String key, Object... values) {
        Long n = redis().opsForSet().add(key, values);
        return n == null ? 0L : n;
    }

    /**
     * 移除 set 元素。
     *
     * @param key    键
     * @param values 元素
     * @return 移除数量
     */
    public static long sRemove(String key, Object... values) {
        Long n = redis().opsForSet().remove(key, values);
        return n == null ? 0L : n;
    }

    /**
     * 判断元素是否在 set 中。
     *
     * @param key   键
     * @param value 元素
     * @return 是否存在
     */
    public static boolean sIsMember(String key, Object value) {
        return Boolean.TRUE.equals(redis().opsForSet().isMember(key, value));
    }

    /**
     * 读取 set 全部成员。
     *
     * @param key 键
     * @return 成员集合
     */
    public static Set<Object> sMembers(String key) {
        return redis().opsForSet().members(key);
    }

    /**
     * set 成员数量。
     *
     * @param key 键
     * @return 数量
     */
    public static long sSize(String key) {
        Long n = redis().opsForSet().size(key);
        return n == null ? 0L : n;
    }

    // ------------------------------------------------------------------ zset（有序集合）

    /**
     * 添加成员及其分数。
     *
     * @param key   键
     * @param value 成员
     * @param score 分数
     * @return 是否新增（false 表示已存在仅更新分数）
     */
    public static boolean zAdd(String key, Object value, double score) {
        return Boolean.TRUE.equals(redis().opsForZSet().add(key, value, score));
    }

    /**
     * 成员分数自增。
     *
     * @param key   键
     * @param value 成员
     * @param delta 增量
     * @return 自增后的分数
     */
    public static double zIncrementScore(String key, Object value, double delta) {
        Double s = redis().opsForZSet().incrementScore(key, value, delta);
        return s == null ? 0d : s;
    }

    /**
     * 按分数升序读取指定排名区间的成员。
     *
     * @param key   键
     * @param start 起始排名
     * @param end   结束排名（-1 末尾）
     * @return 成员集合（有序）
     */
    public static Set<Object> zRange(String key, long start, long end) {
        return redis().opsForZSet().range(key, start, end);
    }

    /**
     * 按分数降序读取指定排名区间的成员（排行榜常用）。
     *
     * @param key   键
     * @param start 起始排名
     * @param end   结束排名（-1 末尾）
     * @return 成员集合（有序）
     */
    public static Set<Object> zReverseRange(String key, long start, long end) {
        return redis().opsForZSet().reverseRange(key, start, end);
    }

    /**
     * 移除 zset 成员。
     *
     * @param key    键
     * @param values 成员
     * @return 移除数量
     */
    public static long zRemove(String key, Object... values) {
        Long n = redis().opsForZSet().remove(key, values);
        return n == null ? 0L : n;
    }
}

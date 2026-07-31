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
package cn.ypbin.starter.cache.redis;

import cn.ypbin.starter.cache.core.CacheService;
import java.time.Duration;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 基于 {@link RedisTemplate} 的缓存实现。
 *
 * <p>{@link #getOrLoad} 内置防击穿（回源加分布式短锁单飞）、防穿透（空值哨兵短时缓存）、
 * 防雪崩（TTL 随机扰动）三重保护。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class RedisCacheService implements CacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);

    /** 空值哨兵：数据源确无数据时缓存此标记，防穿透 */
    private static final String NULL_SENTINEL = "__YPBIN_NULL__";

    /** 空值缓存时长：远短于正常 TTL，避免长期缓存脏空值 */
    private static final Duration NULL_TTL = Duration.ofSeconds(60);

    /** 回源锁时长：足够一次回源即可 */
    private static final Duration LOAD_LOCK_TTL = Duration.ofSeconds(10);

    /** 未抢到回源锁时的等待重试参数 */
    private static final long RETRY_INTERVAL_MILLIS = 50L;
    private static final int MAX_RETRY = 20;

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    public void set(String key, Object value, Duration timeout) {
        redisTemplate.opsForValue().set(key, value, timeout);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = redisTemplate.opsForValue().get(key);
        return value == null ? null : (T) value;
    }

    @Override
    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    @Override
    public long delete(Collection<String> keys) {
        Long count = redisTemplate.delete(keys);
        return count == null ? 0L : count;
    }

    @Override
    public boolean exists(String key) {
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return false;
        }
        // 防穿透哨兵不算"存在有效业务数据"，与 get 返回 null 的语义保持一致
        return !NULL_SENTINEL.equals(redisTemplate.opsForValue().get(key));
    }

    @Override
    public boolean expire(String key, Duration timeout) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, timeout));
    }

    @Override
    public long increment(String key, long delta) {
        Long value = redisTemplate.opsForValue().increment(key, delta);
        return value == null ? 0L : value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOrLoad(String key, Class<T> type, Supplier<T> loader, Duration ttl) {
        // 1. 先读缓存：命中空值哨兵直接返回 null（防穿透），命中真实值直接返回
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return NULL_SENTINEL.equals(cached) ? null : (T) cached;
        }

        // 2. 未命中：抢回源锁，只让一个线程/节点回源（防击穿）
        String lockKey = key + ":ypbin:load-lock";
        String owner = UUID.randomUUID().toString();
        if (tryLoadLock(lockKey, owner)) {
            try {
                // double-check：抢到锁后再读一次，可能已被先到者回填
                Object again = redisTemplate.opsForValue().get(key);
                if (again != null) {
                    return NULL_SENTINEL.equals(again) ? null : (T) again;
                }
                T loaded = loader.get();
                if (loaded == null) {
                    // 防穿透：数据源无数据，缓存空值哨兵短时
                    redisTemplate.opsForValue().set(key, NULL_SENTINEL, NULL_TTL);
                } else {
                    // 防雪崩：TTL 叠加随机扰动
                    redisTemplate.opsForValue().set(key, loaded, jitter(ttl));
                }
                return loaded;
            } finally {
                releaseLoadLock(lockKey, owner);
            }
        }

        // 3. 没抢到锁：短暂等待先到者回填，超时后兜底直接回源（不缓存，避免等待放大）
        return waitForOtherOrLoad(key, type, loader);
    }

    private boolean tryLoadLock(String lockKey, String owner) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, owner, LOAD_LOCK_TTL));
    }

    private void releaseLoadLock(String lockKey, String owner) {
        // 仅释放自己持有的锁
        Object current = redisTemplate.opsForValue().get(lockKey);
        if (owner.equals(current)) {
            redisTemplate.delete(lockKey);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T waitForOtherOrLoad(String key, Class<T> type, Supplier<T> loader) {
        for (int i = 0; i < MAX_RETRY; i++) {
            try {
                Thread.sleep(RETRY_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            Object v = redisTemplate.opsForValue().get(key);
            if (v != null) {
                return NULL_SENTINEL.equals(v) ? null : (T) v;
            }
        }
        // 等待超时：兜底直接回源（不回填，防止把等待压力再转成写压力）
        log.debug("[ypbin-starter] 等待回源锁超时，兜底直接加载：key={}", key);
        return loader.get();
    }

    private Duration jitter(Duration ttl) {
        long base = ttl.toMillis();
        if (base <= 0) {
            return ttl;
        }
        // 叠加 0~10% 随机扰动
        long extra = ThreadLocalRandom.current().nextLong(base / 10 + 1);
        return Duration.ofMillis(base + extra);
    }
}

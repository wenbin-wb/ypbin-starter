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
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 基于 {@link RedisTemplate} 的缓存实现。
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class RedisCacheService implements CacheService {

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
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
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
}

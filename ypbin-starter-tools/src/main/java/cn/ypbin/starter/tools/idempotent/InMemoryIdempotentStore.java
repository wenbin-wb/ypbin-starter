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
package cn.ypbin.starter.tools.idempotent;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于本地内存的幂等存储。
 *
 * <p>单机场景默认实现。用 {@link ConcurrentHashMap#putIfAbsent} 保证占位原子性，
 * 过期键惰性清理（写入时按间隔 CAS 抢占清一次），避免 map 无限膨胀。分布式场景请实现
 * 基于 Redis 的 {@link IdempotentStore} 覆盖本 Bean。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class InMemoryIdempotentStore implements IdempotentStore {

    private static final long CLEANUP_INTERVAL_MILLIS = 60_000L;

    private final ConcurrentHashMap<String, Long> keys = new ConcurrentHashMap<>();

    private final AtomicLong lastCleanup = new AtomicLong(System.currentTimeMillis());

    @Override
    public boolean tryAcquire(String key, Duration expire) {
        long now = System.currentTimeMillis();
        long expireAt = now + expire.toMillis();
        cleanupIfNeeded(now);

        // 原子占位：仅当键不存在，或已存在但已过期时才算首次
        Long existing = keys.putIfAbsent(key, expireAt);
        if (existing == null) {
            return true;
        }
        if (now >= existing) {
            // 已过期：用 replace 抢占续期，成功者视为首次
            return keys.replace(key, existing, expireAt);
        }
        return false;
    }

    private void cleanupIfNeeded(long now) {
        long last = lastCleanup.get();
        if (now - last < CLEANUP_INTERVAL_MILLIS) {
            return;
        }
        if (!lastCleanup.compareAndSet(last, now)) {
            return;
        }
        keys.entrySet().removeIf(entry -> now >= entry.getValue());
    }
}

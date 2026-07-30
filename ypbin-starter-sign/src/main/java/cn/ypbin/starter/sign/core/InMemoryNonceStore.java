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
package cn.ypbin.starter.sign.core;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于本地内存的 nonce 存储。
 *
 * <p>单机默认实现，用 {@link ConcurrentHashMap#putIfAbsent} 保证原子性，带过期与惰性清理，
 * 避免 map 无限膨胀。分布式场景请实现 Redis 版 {@link NonceStore} 覆盖。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class InMemoryNonceStore implements NonceStore {

    private static final long CLEANUP_INTERVAL_MILLIS = 60_000L;

    private final ConcurrentHashMap<String, Long> used = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong(System.currentTimeMillis());

    @Override
    public boolean tryUse(String key, Duration expire) {
        long now = System.currentTimeMillis();
        long expireAt = now + expire.toMillis();
        cleanupIfNeeded(now);
        Long existing = used.putIfAbsent(key, expireAt);
        if (existing == null) {
            return true;
        }
        if (now >= existing) {
            return used.replace(key, existing, expireAt);
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
        used.entrySet().removeIf(e -> now >= e.getValue());
    }
}

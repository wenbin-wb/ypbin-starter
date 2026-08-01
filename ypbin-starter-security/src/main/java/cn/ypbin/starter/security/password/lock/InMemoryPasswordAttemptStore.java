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
package cn.ypbin.starter.security.password.lock;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于本地内存的密码错误计数存储。
 *
 * <p>单机默认实现，带过期与惰性清理，避免 map 无限膨胀。分布式场景请实现 Redis 版
 * {@link PasswordAttemptStore} 覆盖。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class InMemoryPasswordAttemptStore implements PasswordAttemptStore {

    private static final long CLEANUP_INTERVAL_MILLIS = 60_000L;

    private record Counter(long count, long expireAt) {
    }

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong(System.currentTimeMillis());

    @Override
    public long increment(String key, Duration expire) {
        long now = System.currentTimeMillis();
        cleanupIfNeeded(now);
        Counter updated = counters.compute(key, (k, current) -> {
            if (current == null || now >= current.expireAt()) {
                // 首次失败或窗口已过期：从 1 计数，重新设置过期
                return new Counter(1L, now + expire.toMillis());
            }
            // 窗口内累加，保留原过期时间（锁定窗口从首次失败起算）
            return new Counter(current.count() + 1, current.expireAt());
        });
        return updated.count();
    }

    @Override
    public long get(String key) {
        long now = System.currentTimeMillis();
        Counter counter = counters.get(key);
        if (counter == null || now >= counter.expireAt()) {
            return 0L;
        }
        return counter.count();
    }

    @Override
    public long getTimeToLiveSeconds(String key) {
        long now = System.currentTimeMillis();
        Counter counter = counters.get(key);
        if (counter == null || now >= counter.expireAt()) {
            return 0L;
        }
        return (counter.expireAt() - now) / 1000;
    }

    @Override
    public void reset(String key) {
        counters.remove(key);
    }

    @Override
    public void resetByPrefix(String keyPrefix) {
        counters.keySet().removeIf(k -> k.startsWith(keyPrefix));
    }

    private void cleanupIfNeeded(long now) {
        long last = lastCleanup.get();
        if (now - last < CLEANUP_INTERVAL_MILLIS) {
            return;
        }
        if (!lastCleanup.compareAndSet(last, now)) {
            return;
        }
        counters.entrySet().removeIf(e -> now >= e.getValue().expireAt());
    }
}

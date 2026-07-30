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
package cn.ypbin.starter.tools.limiter;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于本地内存的固定窗口限流存储。
 *
 * <p>单机场景默认实现。每个 key 维护一个计数器与窗口过期时间，窗口过期后重置。
 * 分布式场景请实现基于 Redis 的 {@link RateLimiterStore} 覆盖本 Bean。</p>
 *
 * <p>内置惰性清理：过期的 key 若不再访问会长期占用内存（尤其按 IP 限流时 key 基数无上限），
 * 故每隔一定间隔在写入时顺带清除已过期条目，避免 map 无限膨胀导致 OOM。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class InMemoryRateLimiterStore implements RateLimiterStore {

    /** 惰性清理最小间隔（毫秒），避免每次请求都全表扫描 */
    private static final long CLEANUP_INTERVAL_MILLIS = 60_000L;

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    private final AtomicLong lastCleanup = new AtomicLong(System.currentTimeMillis());

    @Override
    public long incrementAndGet(String key, Duration window) {
        long now = System.currentTimeMillis();
        long windowMillis = window.toMillis();
        Window w = windows.compute(key, (k, existing) -> {
            if (existing == null || now >= existing.expireAt) {
                return new Window(now + windowMillis);
            }
            return existing;
        });
        cleanupIfNeeded(now);
        return w.count.incrementAndGet();
    }

    /**
     * 惰性清理过期条目：距上次清理超过间隔时触发，用 CAS 抢占确保同一时刻只有一个线程执行。
     */
    private void cleanupIfNeeded(long now) {
        long last = lastCleanup.get();
        if (now - last < CLEANUP_INTERVAL_MILLIS) {
            return;
        }
        if (!lastCleanup.compareAndSet(last, now)) {
            // 其它线程已抢到清理任务
            return;
        }
        windows.entrySet().removeIf(entry -> now >= entry.getValue().expireAt);
    }

    /**
     * 窗口计数。
     */
    private static final class Window {
        private final long expireAt;
        private final AtomicLong count = new AtomicLong(0);

        Window(long expireAt) {
            this.expireAt = expireAt;
        }
    }
}

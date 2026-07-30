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
 * @author wenbin
 * @since 2026-07-30
 */
public class InMemoryRateLimiterStore implements RateLimiterStore {

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

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
        return w.count.incrementAndGet();
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

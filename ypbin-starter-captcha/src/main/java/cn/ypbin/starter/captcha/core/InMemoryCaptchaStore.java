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
package cn.ypbin.starter.captcha.core;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于本地内存的验证码存储。
 *
 * <p>单机默认实现，带过期与惰性清理。分布式场景请实现 Redis 版 {@link CaptchaStore} 覆盖。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class InMemoryCaptchaStore implements CaptchaStore {

    private static final long CLEANUP_INTERVAL_MILLIS = 60_000L;

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong(System.currentTimeMillis());

    @Override
    public void save(String id, String code, Duration timeout) {
        long now = System.currentTimeMillis();
        cleanupIfNeeded(now);
        store.put(id, new Entry(code, now + timeout.toMillis()));
    }

    @Override
    public String takeAndRemove(String id) {
        Entry entry = store.remove(id);
        if (entry == null || System.currentTimeMillis() >= entry.expireAt) {
            return null;
        }
        return entry.code;
    }

    private void cleanupIfNeeded(long now) {
        long last = lastCleanup.get();
        if (now - last < CLEANUP_INTERVAL_MILLIS) {
            return;
        }
        if (!lastCleanup.compareAndSet(last, now)) {
            return;
        }
        store.entrySet().removeIf(e -> now >= e.getValue().expireAt);
    }

    private record Entry(String code, long expireAt) {
    }
}

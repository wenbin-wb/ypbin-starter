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
package cn.ypbin.starter.messaging.sse;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于本地内存的 SSE 一次性票据存储。
 *
 * <p>单机默认实现，带过期与惰性清理避免膨胀。消费用 {@link ConcurrentHashMap#remove(Object)} 保证同一票据
 * 并发下只有一次成功取出。分布式场景由 Redis 实现 {@link SseTicketStore} 覆盖。</p>
 *
 * @author wenbin
 * @since 2026-08-03
 */
public class InMemorySseTicketStore implements SseTicketStore {

    private static final long CLEANUP_INTERVAL_MILLIS = 60_000L;

    private record Entry(String userId, long expireAt) {
    }

    private final ConcurrentHashMap<String, Entry> tickets = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong(System.currentTimeMillis());

    @Override
    public void save(String ticket, String userId, Duration ttl) {
        long now = System.currentTimeMillis();
        cleanupIfNeeded(now);
        tickets.put(ticket, new Entry(userId, now + ttl.toMillis()));
    }

    @Override
    public Optional<String> consume(String ticket) {
        if (ticket == null || ticket.isEmpty()) {
            return Optional.empty();
        }
        // remove 原子取出，保证一张票只被一个线程消费成功
        Entry entry = tickets.remove(ticket);
        if (entry == null || System.currentTimeMillis() >= entry.expireAt()) {
            return Optional.empty();
        }
        return Optional.of(entry.userId());
    }

    private void cleanupIfNeeded(long now) {
        long last = lastCleanup.get();
        if (now - last < CLEANUP_INTERVAL_MILLIS) {
            return;
        }
        if (!lastCleanup.compareAndSet(last, now)) {
            return;
        }
        tickets.entrySet().removeIf(e -> now >= e.getValue().expireAt());
    }
}

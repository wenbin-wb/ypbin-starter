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
package cn.ypbin.starter.social.core;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/**
 * 进程内 state 存储（默认实现）。
 *
 * <p>有效期默认 {@value #DEFAULT_TTL_SECONDS} 秒——仅需覆盖「跳转到第三方平台并完成授权」的时长；
 * 容量上限 {@value #MAX_ENTRIES} 条，达到上限时先清理过期项，仍然满则淘汰最早到期的一条，
 * 避免发起授权接口被刷时无界增长。</p>
 *
 * @author wenbin
 * @since 2026-11-06
 */
public class InMemorySocialStateStore implements SocialStateStore {

    /** 默认有效期（秒）。 */
    public static final long DEFAULT_TTL_SECONDS = 300L;

    /** 默认容量上限。 */
    public static final int MAX_ENTRIES = 10_000;

    private final Map<String, Instant> states = new ConcurrentHashMap<>();

    private final Duration ttl;

    private final int maxEntries;

    private final Clock clock;

    /**
     * 以默认有效期与容量构造。
     */
    public InMemorySocialStateStore() {
        this(Duration.ofSeconds(DEFAULT_TTL_SECONDS), MAX_ENTRIES, Clock.systemUTC());
    }

    /**
     * 指定有效期、容量与时钟构造（便于测试过期与淘汰）。
     *
     * @param ttl        有效期
     * @param maxEntries 容量上限
     * @param clock      时钟
     */
    public InMemorySocialStateStore(Duration ttl, int maxEntries, Clock clock) {
        this.ttl = ttl;
        this.maxEntries = Math.max(1, maxEntries);
        this.clock = clock;
    }

    @Override
    public void save(String state) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("state 不能为空");
        }
        purgeExpired();
        if (states.size() >= maxEntries) {
            evictEarliest();
        }
        states.put(state, clock.instant().plus(ttl));
    }

    @Override
    public boolean consume(@Nullable String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        Instant expireAt = states.remove(state);
        return expireAt != null && expireAt.isAfter(clock.instant());
    }

    /**
     * 清理已过期项（发起授权是低频操作，顺手 O(n) 清理即可，不引入额外调度）。
     */
    private void purgeExpired() {
        Instant now = clock.instant();
        states.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
    }

    /**
     * 容量已达上限时淘汰最早到期的一条，保证映射有界。
     */
    private void evictEarliest() {
        String earliestKey = null;
        Instant earliest = null;
        for (Map.Entry<String, Instant> entry : states.entrySet()) {
            if (earliest == null || entry.getValue().isBefore(earliest)) {
                earliest = entry.getValue();
                earliestKey = entry.getKey();
            }
        }
        if (earliestKey != null) {
            states.remove(earliestKey);
        }
    }
}

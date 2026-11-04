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
package cn.ypbin.starter.core.blocklist;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 进程内黑名单存储（单机兜底）。
 *
 * <p>无 Redis 时使用；多节点部署下各节点状态不共享，仅适合单实例或开发环境。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
public class InMemoryBlocklistStore implements BlocklistStore {

    private final Clock clock;

    private volatile List<BlocklistRule> rules = List.of();

    private final ConcurrentMap<String, Violation> violations = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Ban> bans = new ConcurrentHashMap<>();

    public InMemoryBlocklistStore() {
        this(Clock.systemUTC());
    }

    InMemoryBlocklistStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public List<BlocklistRule> rules() {
        return rules;
    }

    @Override
    public void replaceRules(Collection<BlocklistRule> newRules) {
        rules = newRules.stream().filter(Objects::nonNull).toList();
    }

    @Override
    public boolean isBanned(String clientIp) {
        Ban ban = bans.get(clientIp);
        if (ban == null) {
            return false;
        }
        if (ban.expireAt().isAfter(clock.instant())) {
            return true;
        }
        bans.remove(clientIp, ban);
        return false;
    }

    @Override
    public void ban(String clientIp, Duration duration, String reason) {
        if (clientIp == null || clientIp.isBlank()) {
            return;
        }
        bans.put(clientIp, new Ban(reason, clock.instant().plus(duration)));
    }

    @Override
    public void unban(String clientIp) {
        bans.remove(clientIp);
    }

    @Override
    public long recordViolation(String clientIp, Duration window, int threshold, Duration banDuration, String reason) {
        if (clientIp == null || clientIp.isBlank() || window.isNegative() || window.isZero() || threshold <= 0) {
            return 0L;
        }
        Instant now = clock.instant();
        Violation counter = violations.compute(clientIp, (key, old) -> {
            if (old == null || !now.isBefore(old.windowStart().plus(window))) {
                return new Violation(now, 1L);
            }
            return new Violation(old.windowStart(), old.count() + 1L);
        });
        if (counter.count() >= threshold) {
            violations.remove(clientIp);
            bans.put(clientIp, new Ban(reason, now.plus(banDuration)));
        }
        return counter.count();
    }

    @Override
    public List<AutoBanEntry> autoBans() {
        Instant now = clock.instant();
        List<AutoBanEntry> result = new ArrayList<>();
        bans.forEach((ip, ban) -> {
            if (ban.expireAt().isAfter(now)) {
                result.add(new AutoBanEntry(ip, ban.reason(), ban.expireAt()));
            } else {
                bans.remove(ip, ban);
            }
        });
        result.sort((left, right) -> right.expireAt().compareTo(left.expireAt()));
        return result;
    }

    private record Violation(Instant windowStart, long count) {
    }

    private record Ban(String reason, Instant expireAt) {
    }
}

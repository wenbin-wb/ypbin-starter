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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 进程内黑名单存储（单机兜底）。
 *
 * <p>无 Redis 时使用；多节点部署下各节点状态不共享，仅适合单实例或开发环境。</p>
 *
 * <p>违规计数带容量上限：窗口过期条目在计数或读取时清理；达到上限后拒绝为新的 IP 计数并
 * 打 debug 日志，避免全网扫描用唯一 IP 把单实例内存撑爆。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
public class InMemoryBlocklistStore implements BlocklistStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryBlocklistStore.class);

    /** 默认违规计数条目上限。 */
    public static final int DEFAULT_MAX_VIOLATION_ENTRIES = 10_000;

    private final Clock clock;

    private final int maxViolationEntries;

    private volatile List<BlocklistRule> rules = List.of();

    private final ConcurrentMap<String, Violation> violations = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Ban> bans = new ConcurrentHashMap<>();

    public InMemoryBlocklistStore() {
        this(Clock.systemUTC(), DEFAULT_MAX_VIOLATION_ENTRIES);
    }

    public InMemoryBlocklistStore(int maxViolationEntries) {
        this(Clock.systemUTC(), maxViolationEntries);
    }

    InMemoryBlocklistStore(Clock clock) {
        this(clock, DEFAULT_MAX_VIOLATION_ENTRIES);
    }

    InMemoryBlocklistStore(Clock clock, int maxViolationEntries) {
        this.clock = clock;
        this.maxViolationEntries = Math.max(1, maxViolationEntries);
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
        violations.remove(clientIp);
    }

    @Override
    public long recordViolation(String clientIp, Duration window, int threshold, Duration banDuration, String reason) {
        if (clientIp == null || clientIp.isBlank() || window.isNegative() || window.isZero() || threshold <= 0) {
            return 0L;
        }
        Instant now = clock.instant();
        if (!violations.containsKey(clientIp) && violations.size() >= maxViolationEntries) {
            violations.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expireAt()));
            if (violations.size() >= maxViolationEntries) {
                log.debug("[ypbin-starter] 访问黑名单违规计数已达上限（{}），本次不计数：ip={}",
                    maxViolationEntries, clientIp);
                return 0L;
            }
        }
        Violation counter = violations.compute(clientIp, (key, old) -> {
            if (old == null || !now.isBefore(old.expireAt())) {
                return new Violation(now.plus(window), 1L);
            }
            return new Violation(old.expireAt(), old.count() + 1L);
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

    private record Violation(Instant expireAt, long count) {
    }

    private record Ban(String reason, Instant expireAt) {
    }
}

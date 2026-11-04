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
package cn.ypbin.starter.security.blocklist;

import cn.ypbin.starter.core.blocklist.AutoBanEntry;
import cn.ypbin.starter.core.blocklist.BlocklistKeys;
import cn.ypbin.starter.core.blocklist.BlocklistRule;
import cn.ypbin.starter.core.blocklist.BlocklistRuleCodec;
import cn.ypbin.starter.core.blocklist.BlocklistStore;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * 基于 Redis 的黑名单存储（多节点共享）。
 *
 * <p>规则存在一个 Hash 中，管理端全量替换时先写临时键再 {@code RENAME}，避免「删旧键→写新键」
 * 之间读到空规则而短暂放行全部流量。读取失败时返回最近一次成功快照（fail-open 的安全边界：
 * 黑名单不可用不应把业务整体打挂）。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
public class RedisBlocklistStore implements BlocklistStore {

    private static final Logger log = LoggerFactory.getLogger(RedisBlocklistStore.class);

    private final StringRedisTemplate redisTemplate;

    private final BlocklistRuleCodec codec;

    private final DefaultRedisScript<Long> violationScript;

    /** 最近一次成功读取的规则快照（Redis 抖动时兜底）。 */
    private volatile List<BlocklistRule> lastKnownRules = List.of();

    public RedisBlocklistStore(StringRedisTemplate redisTemplate, BlocklistRuleCodec codec) {
        this.redisTemplate = redisTemplate;
        this.codec = codec;
        this.violationScript = new DefaultRedisScript<>();
        this.violationScript.setResultType(Long.class);
        this.violationScript.setScriptSource(new ResourceScriptSource(
            new ClassPathResource("META-INF/ypbin/blocklist_violation.lua")));
    }

    @Override
    public List<BlocklistRule> rules() {
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(BlocklistKeys.RULES);
            List<BlocklistRule> decoded = new ArrayList<>(entries.size());
            for (Object value : entries.values()) {
                BlocklistRule rule = codec.decode(value instanceof String text ? text : null);
                if (rule != null) {
                    decoded.add(rule);
                }
            }
            lastKnownRules = List.copyOf(decoded);
            return lastKnownRules;
        } catch (RuntimeException ex) {
            log.warn("[ypbin-starter] 读取访问黑名单规则失败，沿用最近一次快照（{} 条）", lastKnownRules.size(), ex);
            return lastKnownRules;
        }
    }

    @Override
    public void replaceRules(Collection<BlocklistRule> rules) {
        if (rules.isEmpty()) {
            redisTemplate.delete(BlocklistKeys.RULES);
            return;
        }
        Map<String, String> entries = new HashMap<>(rules.size());
        for (BlocklistRule rule : rules) {
            String id = rule.getId();
            entries.put(id == null || id.isBlank() ? UUID.randomUUID().toString() : id, codec.encode(rule));
        }
        String tmpKey = BlocklistKeys.RULES + ":tmp:" + UUID.randomUUID();
        try {
            redisTemplate.opsForHash().putAll(tmpKey, entries);
            redisTemplate.rename(tmpKey, BlocklistKeys.RULES);
        } catch (RuntimeException ex) {
            redisTemplate.delete(tmpKey);
            throw ex;
        }
    }

    @Override
    public boolean isBanned(String clientIp) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(BlocklistKeys.BAN_PREFIX + clientIp));
        } catch (RuntimeException ex) {
            log.warn("[ypbin-starter] 查询自动封禁状态失败，按未封禁处理：ip={}", clientIp, ex);
            return false;
        }
    }

    @Override
    public void ban(String clientIp, Duration duration, String reason) {
        redisTemplate.opsForValue().set(BlocklistKeys.BAN_PREFIX + clientIp, reason, duration);
    }

    @Override
    public void unban(String clientIp) {
        redisTemplate.delete(BlocklistKeys.BAN_PREFIX + clientIp);
    }

    @Override
    public long recordViolation(String clientIp, Duration window, int threshold, Duration banDuration, String reason) {
        if (clientIp == null || clientIp.isBlank() || threshold <= 0) {
            return 0L;
        }
        try {
            Long count = redisTemplate.execute(violationScript,
                List.of(BlocklistKeys.VIOLATION_PREFIX + clientIp, BlocklistKeys.BAN_PREFIX + clientIp),
                String.valueOf(Math.max(1L, window.toSeconds())),
                String.valueOf(threshold),
                reason,
                String.valueOf(Math.max(1L, banDuration.toSeconds())));
            return count == null ? 0L : count;
        } catch (RuntimeException ex) {
            log.warn("[ypbin-starter] 记录访问违规失败：ip={}", clientIp, ex);
            return 0L;
        }
    }

    @Override
    public List<AutoBanEntry> autoBans() {
        List<AutoBanEntry> result = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(BlocklistKeys.BAN_PREFIX + "*").count(200).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext() && result.size() < 500) {
                String key = cursor.next();
                String reason = redisTemplate.opsForValue().get(key);
                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                if (ttl == null || ttl <= 0) {
                    continue;
                }
                result.add(new AutoBanEntry(key.substring(BlocklistKeys.BAN_PREFIX.length()),
                    reason == null ? "" : reason, Instant.now().plusSeconds(ttl)));
            }
        }
        result.sort((left, right) -> right.expireAt().compareTo(left.expireAt()));
        return result;
    }
}

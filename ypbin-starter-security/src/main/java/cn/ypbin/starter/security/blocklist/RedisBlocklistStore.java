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
import cn.ypbin.starter.core.blocklist.BlocklistProperties;
import cn.ypbin.starter.core.blocklist.BlocklistRule;
import cn.ypbin.starter.core.blocklist.BlocklistRuleCodec;
import cn.ypbin.starter.core.blocklist.BlocklistStore;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
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
 * <p><b>Cluster 兼容</b>：规则/临时键/自动封禁索引共用 {@code {rules}} hash tag，单 IP 的违规
 * 计数键与封禁键共用 {@code {ip:<ip>}} hash tag（见 {@link BlocklistKeys}）。自动封禁索引用
 * 单个 Hash 保存「IP → 到期时间|原因」，列表读取不再逐节点 {@code SCAN}，也不再 N+1 查询。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
public class RedisBlocklistStore implements BlocklistStore {

    private static final Logger log = LoggerFactory.getLogger(RedisBlocklistStore.class);

    private static final int MAX_AUTO_BANS = 500;

    private static final String BAN_VALUE_SEPARATOR = "\\|";

    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();

    private final StringRedisTemplate redisTemplate;

    private final BlocklistRuleCodec codec;

    private final DefaultRedisScript<Long> violationScript;

    private final int cacheSeconds;

    /** 最近一次成功读取的规则快照（Redis 抖动时兜底）。 */
    private volatile List<BlocklistRule> cachedRules = List.of();

    /** 最近一次成功读取规则的毫秒时间戳；0 表示尚未成功读取。 */
    private volatile long rulesLoadedAtMillis;

    public RedisBlocklistStore(StringRedisTemplate redisTemplate, BlocklistRuleCodec codec) {
        this(redisTemplate, codec, new BlocklistProperties());
    }

    public RedisBlocklistStore(StringRedisTemplate redisTemplate, BlocklistRuleCodec codec,
                               BlocklistProperties properties) {
        this.redisTemplate = redisTemplate;
        this.codec = codec;
        this.cacheSeconds = Math.max(1, properties.getRulesCacheSeconds());
        this.violationScript = new DefaultRedisScript<>();
        this.violationScript.setResultType(Long.class);
        this.violationScript.setScriptSource(new ResourceScriptSource(
            new ClassPathResource("META-INF/ypbin/blocklist_violation.lua")));
    }

    @Override
    public List<BlocklistRule> rules() {
        long now = System.currentTimeMillis();
        if (rulesLoadedAtMillis > 0 && now - rulesLoadedAtMillis < cacheSeconds * 1000L) {
            return cachedRules;
        }
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(BlocklistKeys.RULES);
            List<BlocklistRule> decoded = new ArrayList<>(entries.size());
            for (Object value : entries.values()) {
                BlocklistRule rule = codec.decode(value instanceof String text ? text : null);
                if (rule != null) {
                    decoded.add(rule);
                }
            }
            cachedRules = List.copyOf(decoded);
            rulesLoadedAtMillis = System.currentTimeMillis();
            return cachedRules;
        } catch (RuntimeException ex) {
            log.warn("[ypbin-starter] 读取访问黑名单规则失败，沿用最近一次快照（{} 条）",
                cachedRules.size(), ex);
            return cachedRules;
        }
    }

    @Override
    public void replaceRules(Collection<BlocklistRule> rules) {
        List<BlocklistRule> snapshot = rules.stream().filter(Objects::nonNull).toList();
        if (snapshot.isEmpty()) {
            redisTemplate.delete(BlocklistKeys.RULES);
            cachedRules = List.of();
            rulesLoadedAtMillis = System.currentTimeMillis();
            return;
        }
        Map<String, String> entries = new HashMap<>(snapshot.size());
        for (BlocklistRule rule : snapshot) {
            String id = rule.getId();
            entries.put(id == null || id.isBlank() ? UUID.randomUUID().toString() : id, codec.encode(rule));
        }
        String tmpKey = BlocklistKeys.RULES + ":tmp:" + UUID.randomUUID();
        try {
            redisTemplate.opsForHash().putAll(tmpKey, entries);
            redisTemplate.rename(tmpKey, BlocklistKeys.RULES);
            cachedRules = snapshot;
            rulesLoadedAtMillis = System.currentTimeMillis();
        } catch (RuntimeException ex) {
            redisTemplate.delete(tmpKey);
            throw ex;
        }
    }

    @Override
    public boolean isBanned(String clientIp) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(BlocklistKeys.ban(clientIp)));
        } catch (RuntimeException ex) {
            log.warn("[ypbin-starter] 查询自动封禁状态失败，按未封禁处理：ip={}", clientIp, ex);
            return false;
        }
    }

    @Override
    public void ban(String clientIp, Duration duration, String reason) {
        Instant expireAt = Instant.now().plus(duration);
        redisTemplate.opsForValue().set(BlocklistKeys.ban(clientIp), reason, duration);
        redisTemplate.opsForHash().put(BlocklistKeys.BAN_INDEX, BlocklistKeys.canonicalIp(clientIp),
            encodeBan(reason, expireAt));
    }

    @Override
    public void unban(String clientIp) {
        redisTemplate.delete(BlocklistKeys.ban(clientIp));
        redisTemplate.opsForHash().delete(BlocklistKeys.BAN_INDEX, BlocklistKeys.canonicalIp(clientIp));
    }

    @Override
    public long recordViolation(String clientIp, Duration window, int threshold, Duration banDuration, String reason) {
        if (clientIp == null || clientIp.isBlank() || threshold <= 0) {
            return 0L;
        }
        String banKey = BlocklistKeys.ban(clientIp);
        try {
            Long count = redisTemplate.execute(violationScript,
                List.of(BlocklistKeys.violation(clientIp), banKey),
                String.valueOf(Math.max(1L, window.toSeconds())),
                String.valueOf(threshold),
                reason,
                String.valueOf(Math.max(1L, banDuration.toSeconds())));
            if (count != null && count >= threshold) {
                syncBanIndex(clientIp, banKey, reason);
            }
            return count == null ? 0L : count;
        } catch (RuntimeException ex) {
            log.warn("[ypbin-starter] 记录访问违规失败：ip={}", clientIp, ex);
            return 0L;
        }
    }

    /**
     * 以真实封禁键的 TTL/原因回填索引：Lua 用 NX 保留更长的既有封禁，索引必须跟随事实。
     */
    private void syncBanIndex(String clientIp, String banKey, String fallbackReason) {
        String reason = redisTemplate.opsForValue().get(banKey);
        Long ttl = redisTemplate.getExpire(banKey, TimeUnit.SECONDS);
        if (ttl == null || ttl <= 0) {
            return;
        }
        redisTemplate.opsForHash().put(BlocklistKeys.BAN_INDEX, BlocklistKeys.canonicalIp(clientIp),
            encodeBan(reason == null ? fallbackReason : reason, Instant.now().plusSeconds(ttl)));
    }

    @Override
    public List<AutoBanEntry> autoBans() {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(BlocklistKeys.BAN_INDEX);
        if (entries.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        List<AutoBanEntry> result = new ArrayList<>();
        List<Object> staleIps = new ArrayList<>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            String ip = String.valueOf(entry.getKey());
            BanRecord record = decodeBan(entry.getValue() instanceof String text ? text : null);
            if (record == null || !record.expireAt().isAfter(now)) {
                staleIps.add(entry.getKey());
                continue;
            }
            if (result.size() < MAX_AUTO_BANS) {
                result.add(new AutoBanEntry(ip, record.reason(), record.expireAt()));
            }
        }
        if (!staleIps.isEmpty()) {
            redisTemplate.opsForHash().delete(BlocklistKeys.BAN_INDEX, staleIps.toArray());
        }
        result.sort((left, right) -> right.expireAt().compareTo(left.expireAt()));
        return result;
    }

    private static String encodeBan(String reason, Instant expireAt) {
        String encodedReason = BASE64_ENCODER.encodeToString(reason.getBytes(StandardCharsets.UTF_8));
        return expireAt.toEpochMilli() + "|" + encodedReason;
    }

    @Nullable
    private static BanRecord decodeBan(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String[] parts = value.split(BAN_VALUE_SEPARATOR, -1);
        if (parts.length != 2) {
            return null;
        }
        try {
            Instant expireAt = Instant.ofEpochMilli(Long.parseLong(parts[0]));
            String reason = new String(BASE64_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
            return new BanRecord(reason, expireAt);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private record BanRecord(String reason, Instant expireAt) {
    }
}

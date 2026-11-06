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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import cn.ypbin.starter.core.blocklist.AutoBanEntry;
import cn.ypbin.starter.core.blocklist.BlocklistKeys;
import cn.ypbin.starter.core.blocklist.BlocklistMatchType;
import cn.ypbin.starter.core.blocklist.BlocklistProperties;
import cn.ypbin.starter.core.blocklist.BlocklistRule;
import cn.ypbin.starter.core.blocklist.BlocklistRuleCodec;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Redis 黑名单存储用例（真实 Redis；无 Docker 时跳过）。
 *
 * @author wenbin
 * @since 2026-11-04
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisBlocklistStoreTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    private static final BlocklistRuleCodec CODEC = new BlocklistRuleCodec();

    private static StringRedisTemplate redis;

    private static RedisBlocklistStore store;

    @BeforeAll
    @SuppressWarnings("resource")
    static void setUp() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
            REDIS.getHost(), REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        BlocklistProperties properties = new BlocklistProperties();
        properties.setRulesCacheSeconds(60);
        store = new RedisBlocklistStore(redis, CODEC, properties);
    }

    @AfterEach
    void clean() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("规则读取在缓存窗口内不重复访问 Redis")
    void rulesAreCached() {
        StringRedisTemplate spyRedis = spy(redis);
        BlocklistProperties properties = new BlocklistProperties();
        properties.setRulesCacheSeconds(60);
        RedisBlocklistStore cachedStore = new RedisBlocklistStore(spyRedis, CODEC, properties);

        cachedStore.rules();
        cachedStore.rules();

        verify(spyRedis, times(1)).opsForHash();
    }

    @Test
    @DisplayName("全量替换后立即可读，空集合删除规则键")
    void replaceRules() {
        BlocklistRule rule = new BlocklistRule("1", BlocklistMatchType.IP, "1.2.3.4",
            null, true, "bad");
        store.replaceRules(List.of(rule));
        assertThat(store.rules()).hasSize(1);

        store.replaceRules(List.of());
        assertThat(store.rules()).isEmpty();
        assertThat(redis.hasKey(BlocklistKeys.RULES)).isFalse();
    }

    @Test
    @DisplayName("手动封禁出现在自动封禁索引，解封后移除")
    void banAndUnban() {
        store.ban("1.2.3.4", Duration.ofMinutes(10), "manual");

        assertThat(store.isBanned("1.2.3.4")).isTrue();
        assertThat(store.autoBans()).extracting(AutoBanEntry::clientIp).contains("1.2.3.4");

        store.unban("1.2.3.4");
        assertThat(store.isBanned("1.2.3.4")).isFalse();
        assertThat(store.autoBans()).isEmpty();
    }

    @Test
    @DisplayName("违规达到阈值后封禁并写入索引")
    void recordViolationBansAndIndexes() {
        store.recordViolation("1.2.3.4", Duration.ofSeconds(60), 2, Duration.ofMinutes(10), "login failure");
        assertThat(store.autoBans()).isEmpty();
        store.recordViolation("1.2.3.4", Duration.ofSeconds(60), 2, Duration.ofMinutes(10), "login failure");

        assertThat(store.isBanned("1.2.3.4")).isTrue();
        assertThat(store.autoBans()).extracting(AutoBanEntry::clientIp).contains("1.2.3.4");
    }

    @Test
    @DisplayName("自动封禁不覆盖更长的人工封禁，也不重复续期")
    void autoBanKeepsLongerManualBan() {
        store.ban("1.2.3.4", Duration.ofHours(1), "manual");
        store.recordViolation("1.2.3.4", Duration.ofSeconds(60), 1, Duration.ofMinutes(10), "login failure");

        assertThat(redis.opsForValue().get(BlocklistKeys.ban("1.2.3.4"))).isEqualTo("manual");
        assertThat(redis.getExpire(BlocklistKeys.ban("1.2.3.4"), TimeUnit.SECONDS))
            .isGreaterThan(3000L);
    }

    @Test
    @DisplayName("自动封禁列表按到期时间倒序返回")
    void autoBansOrdersByExpiry() {
        store.ban("1.1.1.1", Duration.ofMinutes(1), "short");
        store.ban("2.2.2.2", Duration.ofMinutes(10), "long");

        assertThat(store.autoBans()).extracting(AutoBanEntry::clientIp)
            .containsExactly("2.2.2.2", "1.1.1.1");
    }

    @Test
    @DisplayName("窗口/封禁时长为 0 时按最小 1 秒处理")
    void zeroDurationsUseMinimumOneSecond() {
        store.recordViolation("1.2.3.4", Duration.ZERO, 1, Duration.ZERO, "x");

        assertThat(store.isBanned("1.2.3.4")).isTrue();
        assertThat(redis.getExpire(BlocklistKeys.ban("1.2.3.4"), TimeUnit.SECONDS)).isPositive();
    }

    @Test
    @DisplayName("索引中的过期记录在读取时清理")
    void autoBansPrunesExpired() {
        redis.opsForHash().put(BlocklistKeys.BAN_INDEX, "1.2.3.4",
            Instant.now().minusSeconds(1).toEpochMilli() + "|bWFudWFs");

        assertThat(store.autoBans()).isEmpty();
        assertThat(redis.opsForHash().hasKey(BlocklistKeys.BAN_INDEX, "1.2.3.4")).isFalse();
    }
}

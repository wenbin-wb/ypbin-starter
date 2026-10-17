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

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.test.condition.EnabledIfRedisAvailable;
import cn.ypbin.starter.test.container.RedisIntegrationTestSupport;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@code rate_limiter.lua} 的**真实 Redis** 集成测试。
 *
 * <p><b>为什么必须有这一层</b>：此前只有 {@link RateLimiterScriptTest} 的「脚本结构断言」——
 * 它只能证明脚本文本里有 {@code TTL == -1} 分支，不能证明 Redis 真的按该分支执行、也不能证明
 * 并发 INCR 的原子性。本类在真实 Redis 上执行脚本，覆盖：</p>
 * <ul>
 *   <li>{@code current == 1} 时建立窗口（TTL 被设置）；</li>
 *   <li><b>issue #92 的修复点</b>：人为造出「存在但 TTL == -1」的键，一次 INCR 后 TTL 被补上；</li>
 *   <li>返回值语义（当前窗口累计次数）与「后续 INCR 不重置窗口」；</li>
 *   <li>并发下计数原子性（8 线程 × 50 次，返回值恰为 1..400）。</li>
 * </ul>
 *
 * <p>Redis 由共享测试基座提供：外部实例优先（{@code YPBIN_TEST_REDIS_HOST/PORT/PASSWORD}），
 * 否则 Testcontainers 拉起容器；两者都不可用时由 {@link EnabledIfRedisAvailable} **显式跳过**
 * （报告里记为 skipped，而不是静默通过）。本类仅在 {@code mvn -Pit verify} 下执行。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
@EnabledIfRedisAvailable
class RateLimiterLuaRedisIT {

    /** 测试隔离前缀（结束清理）。 */
    private static final String KEY_PREFIX = RedisIntegrationTestSupport.keyPrefix("rate-limiter-lua");

    private static RedisConnectionFactory connectionFactory;

    private static StringRedisTemplate redisTemplate;

    private RedisRateLimiterStore store;

    @BeforeAll
    static void connect() {
        connectionFactory = RedisIntegrationTestSupport.connectionFactory();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
    }

    @AfterAll
    static void disconnect() {
        if (connectionFactory instanceof LettuceConnectionFactory lettuce) {
            lettuce.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        store = new RedisRateLimiterStore(redisTemplate);
    }

    @AfterEach
    void cleanUp() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    @Test
    @DisplayName("首次计数（current==1）建立窗口：TTL 被真正设置")
    void firstIncrementEstablishesWindow() {
        String key = KEY_PREFIX + "first";

        assertThat(store.incrementAndGet(key, Duration.ofSeconds(60))).isEqualTo(1L);
        assertThat(redisTemplate.getExpire(key, TimeUnit.SECONDS))
            .as("窗口必须被建立，否则计数键永不过期")
            .isBetween(1L, 60L);
    }

    @Test
    @DisplayName("★ issue #92 修复点：键存在但 TTL==-1 时，一次 INCR 把过期补上")
    void missingTtlIsBackfilledOnIncrement() {
        String key = KEY_PREFIX + "orphan";
        // 人为造出「存在但没有过期时间」的键：模拟历史版本残留键 / 人工 SET / 主从切换丢 TTL
        redisTemplate.opsForValue().set(key, "5");
        assertThat(redisTemplate.getExpire(key, TimeUnit.SECONDS))
            .as("前置条件：键存在且 TTL == -1")
            .isEqualTo(-1L);

        assertThat(store.incrementAndGet(key, Duration.ofSeconds(60))).isEqualTo(6L);

        assertThat(redisTemplate.getExpire(key, TimeUnit.SECONDS))
            .as("兜底分支必须补 EXPIRE：否则 INCR 永远不等于 1，计数只增不减，该接口被永久限流")
            .isBetween(1L, 60L);
    }

    @Test
    @DisplayName("返回值语义：始终是当前窗口累计次数")
    void returnValueIsCumulativeCount() {
        String key = KEY_PREFIX + "count";

        assertThat(store.incrementAndGet(key, Duration.ofSeconds(60))).isEqualTo(1L);
        assertThat(store.incrementAndGet(key, Duration.ofSeconds(60))).isEqualTo(2L);
        assertThat(store.incrementAndGet(key, Duration.ofSeconds(60))).isEqualTo(3L);
    }

    @Test
    @DisplayName("窗口不被后续 INCR 重置（只有 current==1 / TTL==-1 才 EXPIRE）")
    void windowIsNotExtendedBySubsequentIncrements() {
        String key = KEY_PREFIX + "window";
        store.incrementAndGet(key, Duration.ofSeconds(60));
        Long before = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);

        store.incrementAndGet(key, Duration.ofSeconds(60));
        Long after = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);

        assertThat(after)
            .as("第二次 INCR 不得把窗口重新计时，否则持续请求会让窗口无限延长")
            .isLessThanOrEqualTo(before);
    }

    @Test
    @DisplayName("并发 INCR 原子性：8 线程 × 50 次无丢失，返回值恰为 1..400")
    void concurrentIncrementsAreAtomic() throws Exception {
        String key = KEY_PREFIX + "concurrent";
        int threads = 8;
        int perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<List<Long>>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    List<Long> counts = new ArrayList<>(perThread);
                    for (int j = 0; j < perThread; j++) {
                        counts.add(store.incrementAndGet(key, Duration.ofSeconds(60)));
                    }
                    return counts;
                }));
            }
            start.countDown();

            List<Long> all = new ArrayList<>(threads * perThread);
            for (Future<List<Long>> future : futures) {
                all.addAll(future.get(30, TimeUnit.SECONDS));
            }
            all.sort(Long::compareTo);

            assertThat(all)
                .as("若 INCR 不原子，会出现重复或跳号（丢失更新）")
                .containsExactlyElementsOf(
                    LongStream.rangeClosed(1, (long) threads * perThread).boxed().toList());
            assertThat(redisTemplate.opsForValue().get(key)).isEqualTo(String.valueOf(threads * perThread));
            assertThat(redisTemplate.getExpire(key, TimeUnit.SECONDS)).isPositive();
        } finally {
            pool.shutdownNow();
        }
    }
}

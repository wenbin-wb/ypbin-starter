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
package cn.ypbin.starter.tools.lock;

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
 * {@code lock_release.lua} / {@code lock_renew.lua} 的**真实 Redis** 集成测试。
 *
 * <p><b>为什么必须有这一层</b>：Lua 的归属校验与 {@code PEXPIRE} 只有在真实 Redis 上执行才能验证——
 * mock 掉 {@code StringRedisTemplate} 只能证明「脚本被传了正确的参数」，证明不了「脚本真的按 owner
 * 比对、真的把 TTL 延长、且绝不重建已删除的锁」。本类覆盖：</p>
 * <ul>
 *   <li>{@code lock_release.lua}：只有 owner 匹配才 {@code DEL}（不误删他人的锁）、锁不存在返回 false；</li>
 *   <li>{@code lock_renew.lua}：仍是自己的锁才 {@code PEXPIRE}（真实读回的 TTL 变长）、他人续期无效、
 *       锁不存在/已被删除时绝不重建；</li>
 *   <li>并发抢锁下 {@code SET NX} 只有一个成功。</li>
 * </ul>
 *
 * <p>Redis 由共享测试基座提供：外部实例优先，否则 Testcontainers 拉起容器；两者都不可用时由
 * {@link EnabledIfRedisAvailable} **显式跳过**（记为 skipped，而非静默通过）。仅在 {@code mvn -Pit verify}
 * 下执行。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
@EnabledIfRedisAvailable
class LockLuaRedisIT {

    /** 测试隔离前缀（结束清理）。 */
    private static final String KEY_PREFIX = RedisIntegrationTestSupport.keyPrefix("lock-lua");

    private static RedisConnectionFactory connectionFactory;

    private static StringRedisTemplate redisTemplate;

    private RedisLockService lockService;

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
        lockService = new RedisLockService(redisTemplate);
    }

    @AfterEach
    void cleanUp() {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // ------------------------------------------------------------------ lock_release.lua

    @Test
    @DisplayName("lock_release.lua：只有 owner 匹配才删除，不误删他人的锁")
    void releaseOnlyDeletesOwnLock() {
        String key = KEY_PREFIX + "release";
        assertThat(lockService.tryLock(key, "owner-a", Duration.ofSeconds(30))).isTrue();

        assertThat(lockService.unlock(key, "owner-b")).as("非持有者不得删除").isFalse();
        assertThat(redisTemplate.opsForValue().get(key)).as("他人的锁必须原样保留").isEqualTo("owner-a");

        assertThat(lockService.unlock(key, "owner-a")).isTrue();
        assertThat(redisTemplate.hasKey(key)).isFalse();
    }

    @Test
    @DisplayName("lock_release.lua：锁不存在时返回 false（区分于成功释放）")
    void releaseAbsentKeyReturnsFalse() {
        assertThat(lockService.unlock(KEY_PREFIX + "missing", "owner-a")).isFalse();
    }

    // ------------------------------------------------------------------ lock_renew.lua

    @Test
    @DisplayName("lock_renew.lua：仍是自己的锁才 PEXPIRE（真实 TTL 被延长）")
    void renewExtendsTtlOnlyForOwner() {
        String key = KEY_PREFIX + "renew";
        assertThat(lockService.tryLock(key, "owner-a", Duration.ofSeconds(10))).isTrue();
        Long ttlBefore = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
        assertThat(ttlBefore).isNotNull().isPositive().isLessThanOrEqualTo(10_000L);

        assertThat(lockService.renew(key, "owner-a", Duration.ofSeconds(30))).isTrue();
        Long ttlAfter = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
        assertThat(ttlAfter)
            .as("续期必须真的把 TTL 推后（PEXPIRE 生效）")
            .isGreaterThan(ttlBefore)
            .isLessThanOrEqualTo(30_000L);

        assertThat(lockService.renew(key, "owner-b", Duration.ofSeconds(30)))
            .as("非持有者不得续期")
            .isFalse();
        assertThat(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS))
            .as("他人的续期请求不得改动 TTL")
            .isLessThanOrEqualTo(ttlAfter);
    }

    @Test
    @DisplayName("lock_renew.lua：锁不存在或已被删除时绝不重建（GET==owner 才 PEXPIRE）")
    void renewNeverRecreatesLock() {
        String absent = KEY_PREFIX + "renew-absent";
        assertThat(lockService.renew(absent, "owner-a", Duration.ofSeconds(30))).isFalse();
        assertThat(redisTemplate.hasKey(absent)).isFalse();

        String deleted = KEY_PREFIX + "renew-deleted";
        assertThat(lockService.tryLock(deleted, "owner-a", Duration.ofSeconds(30))).isTrue();
        redisTemplate.delete(deleted);
        assertThat(lockService.renew(deleted, "owner-a", Duration.ofSeconds(30))).isFalse();
        assertThat(redisTemplate.hasKey(deleted))
            .as("续期绝不能把已释放/已过期的锁重新造出来")
            .isFalse();
    }

    @Test
    @DisplayName("lock_renew.lua：锁过期后由他人抢到，原持有者的续期不得作用于新锁")
    void renewMustNotAffectLockTakenByAnotherOwner() throws Exception {
        String key = KEY_PREFIX + "renew-taken-over";
        assertThat(lockService.tryLock(key, "owner-a", Duration.ofMillis(100))).isTrue();

        // 等锁在 Redis 上真实过期，再由 owner-b 抢到
        assertThat(awaitAbsent(key)).as("锁应已过期消失").isTrue();
        assertThat(lockService.tryLock(key, "owner-b", Duration.ofSeconds(10))).isTrue();

        assertThat(lockService.renew(key, "owner-a", Duration.ofSeconds(60)))
            .as("原持有者的续期必须失败，否则两个持有者会同时自以为持锁")
            .isFalse();
        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo("owner-b");
        assertThat(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS)).isLessThanOrEqualTo(10_000L);
    }

    // ------------------------------------------------------------------ 抢锁原子性

    @Test
    @DisplayName("并发抢锁：SET NX 原子性下 8 个竞争者只有一个成功")
    void concurrentTryLockOnlyOneWins() throws Exception {
        String key = KEY_PREFIX + "contended";
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String owner = "owner-" + i;
                futures.add(pool.submit(() -> {
                    start.await();
                    return lockService.tryLock(key, owner, Duration.ofSeconds(30));
                }));
            }
            start.countDown();

            int granted = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(30, TimeUnit.SECONDS)) {
                    granted++;
                }
            }
            assertThat(granted).as("同一把锁同时只能有一个持有者").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 轮询等待键消失（锁真实过期），最多 5 秒。
     *
     * @param key 锁键
     * @return 键已不存在返回 true
     */
    private static boolean awaitAbsent(String key) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (!redisTemplate.hasKey(key)) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }
}

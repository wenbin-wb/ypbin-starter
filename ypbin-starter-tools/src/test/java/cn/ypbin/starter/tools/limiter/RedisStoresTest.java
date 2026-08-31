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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.ypbin.starter.tools.idempotent.RedisIdempotentStore;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Redis 限流与幂等存储测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class RedisStoresTest {

    @SuppressWarnings("unchecked")
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @Test
    void rateLimiterShouldReturnCount() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
            .thenReturn(3L);
        RedisRateLimiterStore store = new RedisRateLimiterStore(redisTemplate);

        assertThat(store.incrementAndGet("rl:key", Duration.ofSeconds(60))).isEqualTo(3L);
    }

    @Test
    void rateLimiterShouldReturnZeroOnNull() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
            .thenReturn(null);
        RedisRateLimiterStore store = new RedisRateLimiterStore(redisTemplate);

        assertThat(store.incrementAndGet("rl:key", Duration.ofSeconds(60))).isZero();
    }

    @Test
    void idempotentShouldAcquireWhenFirst() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
            .thenReturn(1L);
        RedisIdempotentStore store = new RedisIdempotentStore(redisTemplate);

        assertThat(store.tryAcquire("id:key", Duration.ofSeconds(30))).isTrue();
    }

    @Test
    void idempotentShouldRejectOnSecond() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
            .thenReturn(0L);
        RedisIdempotentStore store = new RedisIdempotentStore(redisTemplate);

        assertThat(store.tryAcquire("id:key", Duration.ofSeconds(30))).isFalse();
    }
}

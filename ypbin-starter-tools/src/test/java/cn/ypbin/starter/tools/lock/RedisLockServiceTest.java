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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Redis 分布式锁测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class RedisLockServiceTest {

    @SuppressWarnings("unchecked")
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    @Test
    void tryLockShouldSucceedWhenAbsent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent("lock:key", "owner", Duration.ofSeconds(10))).thenReturn(true);
        RedisLockService service = new RedisLockService(redisTemplate);

        assertThat(service.tryLock("lock:key", "owner", Duration.ofSeconds(10))).isTrue();
    }

    @Test
    void tryLockShouldFailWhenHeld() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent("lock:key", "owner", Duration.ofSeconds(10))).thenReturn(false);
        RedisLockService service = new RedisLockService(redisTemplate);

        assertThat(service.tryLock("lock:key", "owner", Duration.ofSeconds(10))).isFalse();
    }

    @Test
    void unlockShouldReturnTrueWhenReleased() {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any()))
            .thenReturn(1L);
        RedisLockService service = new RedisLockService(redisTemplate);

        assertThat(service.unlock("lock:key", "owner")).isTrue();
    }

    @Test
    void unlockShouldReturnFalseWhenNotOwner() {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any()))
            .thenReturn(0L);
        RedisLockService service = new RedisLockService(redisTemplate);

        assertThat(service.unlock("lock:key", "owner")).isFalse();
    }
}

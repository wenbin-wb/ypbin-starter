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
package cn.ypbin.starter.cache.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * {@link RedisCacheService} 单元测试。
 *
 * @author wenbin
 * @since 2026-08-09
 */
class RedisCacheServiceTest {

    @Test
    void setShouldDelegateToValueOperations() {
        RedisTemplate<String, Object> redisTemplate = redisTemplate();
        ValueOperations<String, Object> valueOperations = valueOperations();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        RedisCacheService service = new RedisCacheService(redisTemplate);

        service.set("k", "v");

        verify(valueOperations).set("k", "v");
    }

    @Test
    void setWithTimeoutShouldDelegate() {
        RedisTemplate<String, Object> redisTemplate = redisTemplate();
        ValueOperations<String, Object> valueOperations = valueOperations();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        RedisCacheService service = new RedisCacheService(redisTemplate);

        service.set("k", "v", Duration.ofSeconds(10));

        verify(valueOperations).set("k", "v", Duration.ofSeconds(10));
    }

    @Test
    void getShouldReturnTypedValue() {
        RedisTemplate<String, Object> redisTemplate = redisTemplate();
        ValueOperations<String, Object> valueOperations = valueOperations();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("k")).thenReturn("value");
        RedisCacheService service = new RedisCacheService(redisTemplate);

        assertThat(service.get("k", String.class)).isEqualTo("value");
    }

    @Test
    void deleteShouldReturnBoolean() {
        RedisTemplate<String, Object> redisTemplate = redisTemplate();
        when(redisTemplate.delete("k")).thenReturn(true);
        RedisCacheService service = new RedisCacheService(redisTemplate);

        assertThat(service.delete("k")).isTrue();
    }

    @Test
    void existsShouldDelegate() {
        RedisTemplate<String, Object> redisTemplate = redisTemplate();
        ValueOperations<String, Object> valueOperations = valueOperations();
        when(redisTemplate.hasKey("k")).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("k")).thenReturn("value");
        RedisCacheService service = new RedisCacheService(redisTemplate);

        assertThat(service.exists("k")).isTrue();
    }

    @Test
    void existsShouldBeFalseForSentinel() {
        RedisTemplate<String, Object> redisTemplate = redisTemplate();
        ValueOperations<String, Object> valueOperations = valueOperations();
        when(redisTemplate.hasKey("k")).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("k")).thenReturn("__YPBIN_NULL__");
        RedisCacheService service = new RedisCacheService(redisTemplate);

        assertThat(service.exists("k")).isFalse();
    }

    @Test
    void setIfAbsentShouldDelegateAtomicallyWithTimeout() {
        RedisTemplate<String, Object> redisTemplate = redisTemplate();
        ValueOperations<String, Object> valueOperations = valueOperations();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Duration timeout = Duration.ofSeconds(60);
        when(valueOperations.setIfAbsent("k", "v", timeout)).thenReturn(true);
        RedisCacheService cache = new RedisCacheService(redisTemplate);

        assertThat(cache.setIfAbsent("k", "v", timeout)).isTrue();
        verify(valueOperations).setIfAbsent("k", "v", timeout);
    }

    @Test
    void compareAndDeleteShouldReturnTrueWhenScriptDeletesValue() {
        RedisTemplate<String, Object> redisTemplate = redisTemplate();
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("k")), eq("v")))
            .thenReturn(1L);
        RedisCacheService cache = new RedisCacheService(redisTemplate);

        assertThat(cache.compareAndDelete("k", "v")).isTrue();
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("k")), eq("v"));
    }

    @Test
    void compareAndDeleteShouldReturnFalseWhenValueDoesNotMatch() {
        RedisTemplate<String, Object> redisTemplate = redisTemplate();
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("k")), eq("wrong")))
            .thenReturn(0L);
        RedisCacheService cache = new RedisCacheService(redisTemplate);

        assertThat(cache.compareAndDelete("k", "wrong")).isFalse();
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, Object> valueOperations() {
        return mock(ValueOperations.class);
    }

    @SuppressWarnings("unchecked")
    private RedisTemplate<String, Object> redisTemplate() {
        return mock(RedisTemplate.class);
    }
}

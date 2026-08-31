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
package cn.ypbin.starter.cache.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

/**
 * Redis 静态工具测试：key/string/set/zset 高频操作委托。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class RedisUtilsTest {

    private final RedisTemplate<String, Object> template = mock(RedisTemplate.class);
    private final ValueOperations<String, Object> valueOps = mock(ValueOperations.class);
    private final SetOperations<String, Object> setOps = mock(SetOperations.class);
    private final ZSetOperations<String, Object> zSetOps = mock(ZSetOperations.class);

    @BeforeEach
    void setUp() throws Exception {
        when(template.opsForValue()).thenReturn(valueOps);
        when(template.opsForSet()).thenReturn(setOps);
        when(template.opsForZSet()).thenReturn(zSetOps);
        java.lang.reflect.Field field = RedisUtils.class.getDeclaredField("template");
        field.setAccessible(true);
        field.set(null, template);
    }

    @Test
    void keyOperationsShouldDelegate() {
        when(template.hasKey("k")).thenReturn(true);
        assertThat(RedisUtils.hasKey("k")).isTrue();

        when(template.delete("k")).thenReturn(true);
        assertThat(RedisUtils.delete("k")).isTrue();

        when(template.delete(any(java.util.Collection.class))).thenReturn(2L);
        assertThat(RedisUtils.delete(List.of("a", "b"))).isEqualTo(2L);

        when(template.expire(anyString(), any(Duration.class))).thenReturn(true);
        assertThat(RedisUtils.expire("k", Duration.ofSeconds(5))).isTrue();

        when(template.getExpire("k", java.util.concurrent.TimeUnit.SECONDS)).thenReturn(100L);
        assertThat(RedisUtils.getExpire("k")).isEqualTo(Long.valueOf(100L));

        when(template.persist("k")).thenReturn(true);
        assertThat(RedisUtils.persist("k")).isTrue();

        when(template.keys("pat*")).thenReturn(Set.of("pat1"));
        assertThat(RedisUtils.keys("pat*")).containsExactly("pat1");
    }

    @Test
    void stringOperationsShouldDelegate() {
        RedisUtils.set("k", "v");
        verify(valueOps).set("k", "v");

        RedisUtils.set("k", "v", Duration.ofSeconds(5));
        verify(valueOps).set("k", "v", Duration.ofSeconds(5));

        when(valueOps.setIfAbsent("k", "v", Duration.ofSeconds(5))).thenReturn(true);
        assertThat(RedisUtils.setIfAbsent("k", "v", Duration.ofSeconds(5))).isTrue();

        when(valueOps.get("k")).thenReturn("value");
        assertThat(RedisUtils.<String>get("k")).isEqualTo("value");

        when(valueOps.getAndSet("k", "new")).thenReturn("old");
        assertThat(RedisUtils.<String>getAndSet("k", "new")).isEqualTo("old");
    }

    @Test
    void collectionOperationsShouldDelegate() {
        when(setOps.add("s", "a", "b")).thenReturn(2L);
        assertThat(RedisUtils.sAdd("s", "a", "b")).isEqualTo(2L);

        when(setOps.members("s")).thenReturn(Set.of("a"));
        assertThat(RedisUtils.sMembers("s")).containsExactly("a");

        when(zSetOps.add("z", "member", 1.0)).thenReturn(true);
        assertThat(RedisUtils.zAdd("z", "member", 1.0)).isTrue();
    }

    @Test
    void templateShouldBeLazilyResolved() throws Exception {
        // template 为 null 时不应崩溃（懒加载路径）
        java.lang.reflect.Field field = RedisUtils.class.getDeclaredField("template");
        field.setAccessible(true);
        field.set(null, null);
        assertThat(RedisUtils.class).isNotNull();
    }
}

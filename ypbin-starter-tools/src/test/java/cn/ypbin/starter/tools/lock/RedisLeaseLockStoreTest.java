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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * {@link RedisLeaseLockStore} 单测：钉住「三条 Lua 的结果映射」与「连接层不确定结果按未抢到处理」。
 *
 * <p>脚本本身的原子语义由 Redis 侧保证（Lua 在服务端串行执行），本测试只验应用侧的取值口径：
 * {@code 1} = 成功、{@code 0} = 失败、{@code null} = **不确定 ⇒ 一律按失败**（绝不冒充持有者）。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
class RedisLeaseLockStoreTest {

    @SuppressWarnings("unchecked")
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    private final RedisLeaseLockStore store = new RedisLeaseLockStore(redisTemplate);

    @Test
    @DisplayName("抢锁：脚本返回 1 ⇒ true；承载 token@expireAt 值与 ttl/now 两个参数")
    void tryLockShouldMapResultAndPassArguments() {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(), any(), any()))
            .thenReturn(1L);

        assertThat(store.tryLock("k", "tok", 5_000L, 1_000L, 60_000L)).isTrue();

        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("k")),
            args.capture(), args.capture(), args.capture());
        assertThat(args.getAllValues())
            .as("锁值必须是 token@expireAt（到期时刻随值携带，抢过期锁才能一条命令完成）")
            .containsExactly("tok@5000", "60000", "1000");
    }

    @Test
    @DisplayName("抢锁：脚本返回 0 或 null 都按未抢到处理（null 属连接层不确定结果）")
    void tryLockShouldTreatZeroAndNullAsFailure() {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(), any(), any()))
            .thenReturn(0L);
        assertThat(store.tryLock("k", "tok", 5_000L, 1_000L, 60_000L)).isFalse();

        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(), any(), any()))
            .thenReturn(null);
        assertThat(store.tryLock("k", "tok", 5_000L, 1_000L, 60_000L))
            .as("不确定时不冒充持有者：按未抢到处理")
            .isFalse();
    }

    @Test
    @DisplayName("续期：脚本返回 1 ⇒ true，否则 false")
    void renewShouldMapResult() {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(), any(), any()))
            .thenReturn(1L);
        assertThat(store.renew("k", "tok", 9_000L, 60_000L)).isTrue();

        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(), any(), any()))
            .thenReturn(0L);
        assertThat(store.renew("k", "tok", 9_000L, 60_000L)).isFalse();
    }

    @Test
    @DisplayName("释放：脚本返回 1 ⇒ true，0 ⇒ false（不是自己的锁不删）")
    void unlockShouldMapResult() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("k")), eq("tok")))
            .thenReturn(1L);
        assertThat(store.unlock("k", "tok")).isTrue();

        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("k")), eq("tok")))
            .thenReturn(0L);
        assertThat(store.unlock("k", "tok")).isFalse();
    }

    @Test
    @DisplayName("释放：脚本返回 null 按未释放处理（不误报成功）")
    void unlockShouldTreatNullAsFailure() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of("k")), eq("tok")))
            .thenReturn(null);
        assertThat(store.unlock("k", "tok")).isFalse();
    }
}

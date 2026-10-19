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
package cn.ypbin.starter.sign.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * {@link RedisNonceStore} 的 <b>fail-closed</b> 语义测试（2026-10-19 反哺自 {@code ypbin-iot}）。
 *
 * <p><b>为什么要钉住</b>：nonce 防重放是<b>安全校验</b>。Redis 不可用时若异常上抛，
 * {@code SignChecker#check} 会整体异常退出——那既不是「放行」也不是「拒绝」，而是未定义形态，
 * 且把中间件故障放大成业务不可用。正确语义是明确拒绝（返回 {@code false}），
 * 由上层按签名校验失败处理。宁可拒绝合法请求（可重试），也绝不在「无法判断是否重放」时放行。</p>
 *
 * @author wenbin
 * @since 2026-10-19
 */
class RedisNonceStoreFailClosedTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    private final RedisNonceStore store = new RedisNonceStore(redisTemplate);

    @Test
    @DisplayName("Redis 连接异常：按拒绝处理（返回 false），不向上抛异常")
    @SuppressWarnings("unchecked")
    void redisFailureShouldDenyInsteadOfThrow() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
            .thenThrow(new RedisConnectionFailureException("redis down"));

        // 关键断言：不抛异常，且返回 false（拒绝放行）
        assertThat(store.tryUse("ak:nonce-1", Duration.ofSeconds(60)))
            .as("Redis 不可用时必须 fail-closed：拒绝本次请求，而不是抛异常或放行")
            .isFalse();
    }

    @Test
    @DisplayName("Lua 返回 null（连接层面不确定）：同样按拒绝处理")
    @SuppressWarnings("unchecked")
    void nullScriptResultShouldDeny() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(null);

        assertThat(store.tryUse("ak:nonce-2", Duration.ofSeconds(60)))
            .as("返回 null 属异常语义，不能当作「首次使用」放行")
            .isFalse();
    }

    @Test
    @DisplayName("正常路径：返回 1 表示首次使用（放行），返回 0 表示重放（拒绝）")
    @SuppressWarnings("unchecked")
    void normalPathsShouldKeepOriginalSemantics() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);
        assertThat(store.tryUse("ak:nonce-3", Duration.ofSeconds(60))).isTrue();

        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(0L);
        assertThat(store.tryUse("ak:nonce-4", Duration.ofSeconds(60))).isFalse();
    }
}

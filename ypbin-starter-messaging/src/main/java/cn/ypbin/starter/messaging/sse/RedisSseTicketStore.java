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
package cn.ypbin.starter.messaging.sse;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 基于 Redis 的分布式 SSE 一次性票据存储。
 *
 * <p>消费用 Lua 脚本原子执行 {@code GET + DEL}，保证同一票据在多节点/高并发下只被成功消费一次，从根源
 * 杜绝重放。仅在存在 Redis 时装配。</p>
 *
 * @author wenbin
 * @since 2026-08-03
 */
public class RedisSseTicketStore implements SseTicketStore {

    /** 键前缀，避免与业务键冲突 */
    private static final String KEY_PREFIX = "ypbin:sse:ticket:";

    /** 原子消费：返回值并删除，不存在返回 nil */
    private static final String CONSUME_LUA =
        "local v = redis.call('get', KEYS[1]); if v then redis.call('del', KEYS[1]); end; return v;";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<String> consumeScript;

    public RedisSseTicketStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.consumeScript = new DefaultRedisScript<>(CONSUME_LUA, String.class);
    }

    @Override
    public void save(String ticket, String userId, Duration ttl) {
        redisTemplate.opsForValue().set(KEY_PREFIX + ticket, userId, ttl);
    }

    @Override
    public Optional<String> consume(String ticket) {
        if (ticket == null || ticket.isEmpty()) {
            return Optional.empty();
        }
        String userId = redisTemplate.execute(
            consumeScript, Collections.singletonList(KEY_PREFIX + ticket));
        return Optional.ofNullable(userId);
    }
}

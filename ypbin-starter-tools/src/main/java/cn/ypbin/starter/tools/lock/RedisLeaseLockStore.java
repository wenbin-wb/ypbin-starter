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

import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 基于 Redis 的租约锁存储（多实例实现）。
 *
 * <p><b>原子性靠 Lua</b>：三条脚本分别完成「不存在或已过期则抢」「仍属于自己则续期」
 * 「仍属于自己则删除」。绝不做「先 GET 再 SET」——那是竞态：两个副本可能同时通过判断，
 * 于是两个人同时持锁。</p>
 *
 * <p><b>到期判定用调用方传入的 {@code nowMillis}</b>（而不是 Redis 的 {@code TIME}）：
 * ① 让「可注入时钟」这条测试路径在真实 Redis 上也成立；② 各副本用同一套应用时钟判定，
 * 行为可解释。代价是副本间时钟偏移会让「谁先抢到过期的锁」略有不同——对幂等后台任务是可接受边界；
 * Redis 侧的 {@code PX} 只是兜底，防止应用时钟异常时键永久残留。</p>
 *
 * <p><b>结果判空</b>：脚本返回 {@code null} 属连接层的不确定结果，一律按「没抢到」处理
 * （与 {@code RedisNonceStore} 的 fail-closed 取向一致：不确定时不冒充持有者）。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
class RedisLeaseLockStore implements LeaseLockStore {

    /**
     * 抢锁：不存在、或已过期（{@code expireAt <= now}）时写入 {@code token@expireAt}；
     * 否则保持原值。
     */
    private static final String ACQUIRE_LUA = """
        local current = redis.call('GET', KEYS[1])
        if current == false then
          redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
          return 1
        end
        local at = string.find(current, '@', 1, true)
        if at ~= nil then
          local expireAt = tonumber(string.sub(current, at + 1))
          if expireAt ~= nil and expireAt <= tonumber(ARGV[3]) then
            redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2])
            return 1
          end
        end
        return 0
        """;

    /**
     * 续期：仍是自己的 token 时改写到期时刻。
     */
    private static final String RENEW_LUA = """
        local current = redis.call('GET', KEYS[1])
        if current == false then
          return 0
        end
        local at = string.find(current, '@', 1, true)
        if at == nil then
          return 0
        end
        if string.sub(current, 1, at - 1) == ARGV[1] then
          redis.call('SET', KEYS[1], ARGV[1] .. '@' .. ARGV[2], 'PX', ARGV[3])
          return 1
        end
        return 0
        """;

    /**
     * 释放：仍是自己的 token 时删除（不是自己的锁绝不删）。
     */
    private static final String UNLOCK_LUA = """
        local current = redis.call('GET', KEYS[1])
        if current == false then
          return 0
        end
        local at = string.find(current, '@', 1, true)
        if at == nil then
          return 0
        end
        if string.sub(current, 1, at - 1) == ARGV[1] then
          return redis.call('DEL', KEYS[1])
        end
        return 0
        """;

    private final StringRedisTemplate redisTemplate;

    private final DefaultRedisScript<Long> acquireScript;

    private final DefaultRedisScript<Long> renewScript;

    private final DefaultRedisScript<Long> unlockScript;

    RedisLeaseLockStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.acquireScript = new DefaultRedisScript<>(ACQUIRE_LUA, Long.class);
        this.renewScript = new DefaultRedisScript<>(RENEW_LUA, Long.class);
        this.unlockScript = new DefaultRedisScript<>(UNLOCK_LUA, Long.class);
    }

    @Override
    public boolean tryLock(String key, String token, long expireAtMillis, long nowMillis, long ttlMillis) {
        Long result = redisTemplate.execute(acquireScript, List.of(key),
            value(token, expireAtMillis), String.valueOf(ttlMillis), String.valueOf(nowMillis));
        return result != null && result == 1L;
    }

    @Override
    public boolean renew(String key, String token, long newExpireAtMillis, long ttlMillis) {
        Long result = redisTemplate.execute(renewScript, List.of(key), token,
            String.valueOf(newExpireAtMillis), String.valueOf(ttlMillis));
        return result != null && result == 1L;
    }

    @Override
    public boolean unlock(String key, String token) {
        Long result = redisTemplate.execute(unlockScript, List.of(key), token);
        return result != null && result == 1L;
    }

    /**
     * 锁值的形态：{@code token@expireAtMillis}（token 不含 {@code @}，解析无歧义）。
     *
     * @param token          持有者标识
     * @param expireAtMillis 到期时刻
     * @return 存储值
     */
    private static String value(String token, long expireAtMillis) {
        return token + '@' + expireAtMillis;
    }
}

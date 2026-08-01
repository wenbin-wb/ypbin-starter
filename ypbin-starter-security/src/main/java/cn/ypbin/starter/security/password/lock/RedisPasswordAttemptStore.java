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
package cn.ypbin.starter.security.password.lock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * 基于 Redis 的分布式密码错误计数存储。
 *
 * <p>用 Lua 脚本原子执行 {@code INCR}，仅在首次失败（计数为 1）时设置过期，保证锁定窗口从首次失败
 * 起算，多节点共享锁定状态。仅在存在 Redis 时装配。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class RedisPasswordAttemptStore implements PasswordAttemptStore {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> incrScript;

    public RedisPasswordAttemptStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.incrScript = new DefaultRedisScript<>();
        this.incrScript.setResultType(Long.class);
        this.incrScript.setScriptSource(new ResourceScriptSource(
            new ClassPathResource("META-INF/ypbin/password_attempt_incr.lua")));
    }

    @Override
    public long increment(String key, Duration window, int threshold, Duration lockDuration) {
        Long result = redisTemplate.execute(incrScript,
            Collections.singletonList(key),
            String.valueOf(window.toSeconds()),
            String.valueOf(threshold),
            String.valueOf(lockDuration.toSeconds()));
        return result == null ? 0L : result;
    }

    @Override
    public long get(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Override
    public long getTimeToLiveSeconds(String key) {
        Long ttl = redisTemplate.getExpire(key);
        return (ttl == null || ttl < 0) ? 0L : ttl;
    }

    @Override
    public void reset(String key) {
        redisTemplate.delete(key);
    }

    @Override
    public void resetByPrefix(String keyPrefix) {
        // 用 SCAN 而非 KEYS，避免阻塞 Redis
        ScanOptions options = ScanOptions.scanOptions().match(keyPrefix + "*").count(200).build();
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}

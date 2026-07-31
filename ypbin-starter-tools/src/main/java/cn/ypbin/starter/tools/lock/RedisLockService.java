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

import java.time.Duration;
import java.util.Collections;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * 基于 Redis 的分布式锁实现。
 *
 * <p>加锁用 {@code SET key owner NX EX ttl} 原子占位；释放用 Lua 脚本先比对持有者再删除，
 * 避免误删他人的锁。支持多节点微服务共享同一把锁。仅在存在 Redis 时装配。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class RedisLockService implements LockService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> releaseScript;

    public RedisLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.releaseScript = new DefaultRedisScript<>();
        this.releaseScript.setResultType(Long.class);
        this.releaseScript.setScriptSource(new ResourceScriptSource(
            new ClassPathResource("META-INF/ypbin/lock_release.lua")));
    }

    @Override
    public boolean tryLock(String key, String owner, Duration ttl) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, owner, ttl);
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public boolean unlock(String key, String owner) {
        Long released = redisTemplate.execute(releaseScript, Collections.singletonList(key), owner);
        return released != null && released > 0;
    }
}

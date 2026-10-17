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
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * 基于 Redis 的分布式锁实现。
 *
 * <p>加锁用 {@code SET key owner NX PX ttl} 原子占位；续期与释放各用一段 Lua 脚本，先原子比对
 * 持有者再 {@code PEXPIRE}/{@code DEL}，避免误续/误删他人的锁。支持多节点微服务共享同一把锁。
 * 仅在存在 Redis 时装配。</p>
 *
 * <p><b>为什么续期/释放必须是 Lua</b>：{@code GET} 与随后的 {@code PEXPIRE}/{@code DEL} 若分两条命令，
 * 中间锁可能过期并被他人抢走，自己随后的动作就会作用到他人的锁上。脚本内 {@code GET == owner}
 * 之后才动作，且锁不存在时**绝不重建**。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class RedisLockService implements LockService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> renewScript;
    private final DefaultRedisScript<Long> releaseScript;

    public RedisLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.renewScript = script("META-INF/ypbin/lock_renew.lua");
        this.releaseScript = script("META-INF/ypbin/lock_release.lua");
    }

    @Override
    public boolean tryLock(String key, String owner, Duration ttl) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, owner, ttl);
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public boolean renew(String key, String owner, Duration ttl) {
        Long renewed = redisTemplate.execute(renewScript, List.of(key), owner, String.valueOf(ttl.toMillis()));
        return renewed != null && renewed > 0;
    }

    @Override
    public boolean unlock(String key, String owner) {
        Long released = redisTemplate.execute(releaseScript, List.of(key), owner);
        return released != null && released > 0;
    }

    /**
     * 加载 classpath 上的 Lua 脚本（结果类型统一为 {@code Long}）。
     *
     * @param location 脚本路径
     * @return Redis 脚本
     */
    private static DefaultRedisScript<Long> script(String location) {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setResultType(Long.class);
        redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource(location)));
        return redisScript;
    }
}

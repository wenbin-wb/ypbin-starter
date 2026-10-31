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
package cn.ypbin.starter.tools.idempotent;

import java.time.Duration;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * 基于 Redis 的分布式幂等存储。
 *
 * <p>用 Lua 脚本原子执行 {@code SET NX EX}，保证占位与过期设置的原子性，
 * 支持多节点微服务共享幂等状态。仅在存在 Redis 时装配。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class RedisIdempotentStore implements IdempotentStore {

    /** 未指定属主的占位（历史两参 API）统一登记为该值。 */
    private static final String ANONYMOUS_OWNER = "1";

    /** 释放脚本位置：与分布式锁共用同一份 compare-and-delete，避免安全敏感原语出现两份实现。 */
    private static final String RELEASE_SCRIPT_LOCATION = "META-INF/ypbin/compare_and_delete.lua";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> script;
    private final DefaultRedisScript<Long> releaseScript;

    public RedisIdempotentStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>();
        this.script.setResultType(Long.class);
        this.script.setScriptSource(new ResourceScriptSource(
            new ClassPathResource("META-INF/ypbin/idempotent.lua")));
        this.releaseScript = new DefaultRedisScript<>();
        this.releaseScript.setResultType(Long.class);
        this.releaseScript.setScriptSource(
            new ResourceScriptSource(new ClassPathResource(RELEASE_SCRIPT_LOCATION)));
    }

    @Override
    public boolean tryAcquire(String key, Duration expire) {
        return tryAcquire(key, expire, ANONYMOUS_OWNER);
    }

    @Override
    public boolean tryAcquire(String key, Duration expire, String owner) {
        Long result = redisTemplate.execute(script,
            List.of(key),
            owner,
            String.valueOf(expire.toSeconds()));
        return result != null && result == 1L;
    }

    @Override
    public void release(String key) {
        redisTemplate.delete(key);
    }

    @Override
    public void release(String key, String owner) {
        // 窗口过期后占位可能已被下一个请求重新占用：按值比对后删除，绝不无条件 DEL
        redisTemplate.execute(releaseScript, List.of(key), owner);
    }
}

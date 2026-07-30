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
package cn.ypbin.starter.tools.limiter;

import java.time.Duration;
import java.util.Collections;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * 基于 Redis + Lua 脚本的分布式限流存储。
 *
 * <p>计数与设置过期通过一段 Lua 脚本原子执行，避免"先 INCR 再 EXPIRE"两步之间的竞态，
 * 支持多节点微服务共享同一限流窗口。仅在存在 Redis 时装配。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class RedisRateLimiterStore implements RateLimiterStore {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> script;

    public RedisRateLimiterStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>();
        this.script.setResultType(Long.class);
        this.script.setScriptSource(new ResourceScriptSource(
            new ClassPathResource("META-INF/ypbin/rate_limiter.lua")));
    }

    @Override
    public long incrementAndGet(String key, Duration window) {
        Long count = redisTemplate.execute(script,
            Collections.singletonList(key),
            String.valueOf(window.toSeconds()));
        return count == null ? 0L : count;
    }
}

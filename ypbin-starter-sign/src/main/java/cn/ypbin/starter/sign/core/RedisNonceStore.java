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

import java.time.Duration;
import java.util.Collections;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * 基于 Redis 的分布式 nonce 存储。
 *
 * <p>用 Lua 脚本原子执行 {@code SET NX EX}，多节点共享防重放状态。仅在存在 Redis 时装配。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class RedisNonceStore implements NonceStore {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> script;

    public RedisNonceStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>();
        this.script.setResultType(Long.class);
        this.script.setScriptSource(new ResourceScriptSource(
            new ClassPathResource("META-INF/ypbin/sign_nonce.lua")));
    }

    @Override
    public boolean tryUse(String key, Duration expire) {
        Long result = redisTemplate.execute(script,
            Collections.singletonList(key),
            String.valueOf(expire.toSeconds()));
        return result != null && result == 1L;
    }
}

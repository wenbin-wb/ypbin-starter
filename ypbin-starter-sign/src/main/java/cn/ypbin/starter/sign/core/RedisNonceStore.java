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

import cn.ypbin.starter.core.util.LogSanitizer;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * 基于 Redis 的分布式 nonce 存储。
 *
 * <p>用 Lua 脚本原子执行 {@code SET NX EX}，多节点共享防重放状态。仅在存在 Redis 时装配。</p>
 *
 * <p><strong>失败语义：fail-closed（拒绝放行），且不向上抛异常</strong>（2026-10-19 反哺自
 * {@code ypbin-iot} 的同名实现）。防重放是<b>安全校验</b>：Redis 不可用时若抛异常，调用方
 * （{@code SignChecker#check}）会整体异常退出——那既不是「放行」也不是「拒绝」，而是
 * <b>未定义的失败形态</b>，且会把中间件故障放大成业务不可用。正确语义是明确的「拒绝本次请求」：
 * 返回 {@code false}（等价于「nonce 已用过 ⇒ 重放」），由上层按签名校验失败处理。
 * 宁可拒绝合法请求（可重试、可告警），也绝不在「无法判断是否重放」时放行（那是安全漏洞）。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class RedisNonceStore implements NonceStore {

    private static final Logger log = LoggerFactory.getLogger(RedisNonceStore.class);

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
        try {
            Long result = redisTemplate.execute(script,
                List.of(key),
                String.valueOf(expire.toSeconds()));
            // 结果为 null 属 Lua 脚本的异常语义（连接层面不确定）⇒ 同样按拒绝处理：
            // 「拿不准是否重放」时不能放行，那不是可以赌的场景。
            return result != null && result == 1L;
        } catch (RuntimeException ex) {
            // key 含第三方提供的 accessKey/nonce ⇒ 必须 sanitize 后再进日志，
            // 防换行/控制字符跨行伪造日志（CodeQL: Log Injection）。
            log.error("[ypbin-starter] nonce 防重放存储不可用，按拒绝处理（fail-closed，不放行）：key={}",
                LogSanitizer.sanitize(key), ex);
            return false;
        }
    }
}

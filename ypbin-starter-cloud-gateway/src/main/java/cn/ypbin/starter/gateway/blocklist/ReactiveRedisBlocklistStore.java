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
package cn.ypbin.starter.gateway.blocklist;

import cn.ypbin.starter.core.blocklist.BlocklistKeys;
import cn.ypbin.starter.core.blocklist.BlocklistProperties;
import cn.ypbin.starter.core.blocklist.BlocklistRule;
import cn.ypbin.starter.core.blocklist.BlocklistRuleCodec;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import reactor.core.publisher.Mono;

/**
 * 网关侧 Redis 黑名单存储。
 *
 * <p>规则读取带本地缓存（{@code rules-cache-seconds}，默认 5 秒），避免每个请求都打一次 Redis；
 * 规则发布后最迟一个缓存周期生效。读取失败返回最近一次快照，封禁查询失败按未封禁处理，
 * 保证 Redis 抖动不会把网关整体拦住。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
public class ReactiveRedisBlocklistStore implements ReactiveBlocklistStore {

    private static final Logger log = LoggerFactory.getLogger(ReactiveRedisBlocklistStore.class);

    private final ReactiveStringRedisTemplate redis;

    private final BlocklistRuleCodec codec;

    private final int cacheSeconds;

    private final DefaultRedisScript<Long> violationScript;

    private volatile List<BlocklistRule> cachedRules = List.of();

    private volatile long loadedAtMillis;

    public ReactiveRedisBlocklistStore(ReactiveStringRedisTemplate redis, BlocklistRuleCodec codec,
                                       BlocklistProperties properties) {
        this.redis = redis;
        this.codec = codec;
        this.cacheSeconds = Math.max(1, properties.getRulesCacheSeconds());
        this.violationScript = new DefaultRedisScript<>();
        this.violationScript.setResultType(Long.class);
        this.violationScript.setScriptSource(new ResourceScriptSource(
            new ClassPathResource("META-INF/ypbin/blocklist_violation.lua")));
    }

    @Override
    public Mono<List<BlocklistRule>> rules() {
        long now = System.currentTimeMillis();
        if (now - loadedAtMillis < cacheSeconds * 1000L) {
            return Mono.just(cachedRules);
        }
        return redis.<String, String>opsForHash().entries(BlocklistKeys.RULES)
            .map(entry -> codec.decode(entry.getValue()))
            .filter(Objects::nonNull)
            .collectList()
            .doOnNext(rules -> {
                cachedRules = List.copyOf(rules);
                loadedAtMillis = System.currentTimeMillis();
            })
            .onErrorResume(ex -> {
                log.warn("[ypbin-starter] 网关读取黑名单规则失败，沿用最近一次快照（{} 条）",
                    cachedRules.size(), ex);
                return Mono.just(cachedRules);
            });
    }

    @Override
    public Mono<Boolean> isBanned(String clientIp) {
        return redis.hasKey(BlocklistKeys.ban(clientIp))
            .defaultIfEmpty(false)
            .onErrorResume(ex -> {
                log.warn("[ypbin-starter] 网关查询自动封禁状态失败，按未封禁处理：ip={}", clientIp, ex);
                return Mono.just(false);
            });
    }

    @Override
    public Mono<Long> recordViolation(String clientIp, Duration window, int threshold, Duration banDuration,
                                      String reason) {
        return redis.execute(violationScript,
                List.of(BlocklistKeys.violation(clientIp), BlocklistKeys.ban(clientIp)),
                String.valueOf(Math.max(1L, window.toSeconds())),
                String.valueOf(threshold),
                reason,
                String.valueOf(Math.max(1L, banDuration.toSeconds())))
            .next()
            .defaultIfEmpty(0L)
            .onErrorResume(ex -> {
                log.warn("[ypbin-starter] 网关记录访问违规失败：ip={}", clientIp, ex);
                return Mono.just(0L);
            });
    }

}

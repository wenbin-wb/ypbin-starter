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
package cn.ypbin.starter.gateway.ratelimit;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * 属性驱动的固定窗口限流/配额过滤器（看板反哺：ypbin-iot 开放 API 限流的通用部分）。
 *
 * <p>算法：Redis {@code INCR + EXPIRE}（QPS 秒级窗口 + 自然日配额），不引入令牌桶依赖。
 * 维度键与配额由上游鉴权过滤器经 exchange attributes 供给（见 {@link RateLimitProperties}），
 * 本过滤器不解析任何业务身份。Redis 异常 fail-open（仅记日志，不阻断业务）。</p>
 *
 * @author wenbin
 * @since 2026-10-02
 */
public class AttributeRateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AttributeRateLimitGlobalFilter.class);

    private final ReactiveStringRedisTemplate redis;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public AttributeRateLimitGlobalFilter(ReactiveStringRedisTemplate redis,
                                          RateLimitProperties properties,
                                          ObjectMapper objectMapper) {
        this.redis = redis;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (properties.getPathPrefixes().stream().noneMatch(path::startsWith)) {
            return chain.filter(exchange);
        }
        String dimension = exchange.getAttribute(properties.getKeyAttribute());
        if (dimension == null || dimension.isEmpty()) {
            return chain.filter(exchange);
        }
        int qpsLimit = attrInt(exchange, properties.getQpsAttribute(), properties.getDefaultQps());
        int quotaLimit = attrInt(exchange, properties.getQuotaAttribute(), properties.getDefaultQuota());
        long nowSec = System.currentTimeMillis() / 1000L;
        String qpsKey = properties.getQpsKeyPrefix() + dimension + ":" + nowSec;
        String quotaKey = properties.getQuotaKeyPrefix() + dimension + ":" + LocalDate.now();
        long quotaTtlSec = ChronoUnit.SECONDS.between(LocalDateTime.now(),
            LocalDate.now().plusDays(1).atStartOfDay()) + 1;

        return incr(qpsKey, properties.getWindowSeconds() + 1L).flatMap(qpsCount ->
            incr(quotaKey, quotaTtlSec).flatMap(quotaCount -> {
                FixedWindowRateLimit qpsDecision = FixedWindowRateLimit.forCount(qpsCount, qpsLimit);
                FixedWindowRateLimit quotaDecision = FixedWindowRateLimit.forCount(quotaCount, quotaLimit);
                if (!qpsDecision.allowed() || !quotaDecision.allowed()) {
                    log.warn("[starter] rate limited: dimension={} qps={}/{} quota={}/{}",
                        dimension, qpsCount, qpsLimit, quotaCount, quotaLimit);
                    return reject(exchange, "请求过于频繁或超过日配额，请稍后重试（QPS 上限 " + qpsLimit
                        + "/" + properties.getWindowSeconds() + "s，日配额 " + quotaLimit + "）");
                }
                return chain.filter(exchange);
            }));
    }

    private Mono<Long> incr(String key, long ttlSeconds) {
        return redis.opsForValue().increment(key)
            .flatMap(count -> count == 1L
                ? redis.expire(key, Duration.ofSeconds(Math.max(1, ttlSeconds))).thenReturn(count)
                : Mono.just(count))
            .onErrorReturn(-1L);
    }

    private static int attrInt(ServerWebExchange exchange, String name, int def) {
        Object value = exchange.getAttribute(name);
        if (value instanceof Integer number) {
            return number;
        }
        return def;
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(
                Map.of("code", 429, "message", message, "success", false));
        } catch (Exception ex) {
            body = "too many requests".getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return properties.getOrder();
    }
}

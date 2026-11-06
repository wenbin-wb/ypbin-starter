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

import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.core.util.LogSanitizer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * 属性驱动的固定窗口限流/配额过滤器（看板反哺：ypbin-iot 开放 API 限流的通用部分）。
 *
 * <p>算法：Redis 固定窗口计数（窗口内上限 = QPS 配额）+ 自然日配额，不引入令牌桶依赖。
 * 维度键与配额由上游鉴权过滤器经 exchange attributes 供给（见 {@link RateLimitProperties}），
 * 本过滤器不解析任何业务身份。</p>
 *
 * <p><strong>计数必须原子</strong>：{@code INCR} 与 {@code EXPIRE} 在同一个 Lua 脚本内完成
 * （{@code META-INF/ypbin/gateway_rate_limit.lua}）。分两次调用时若进程在两次命令之间退出、
 * 或 {@code EXPIRE} 因网络抖动失败，键会永久驻留且计数只增不减——该维度被<b>永久限流</b>，
 * 且重启不会自愈。脚本另带 TTL 自愈：键存在但没有过期时间时补设一次。</p>
 *
 * <p><strong>窗口语义</strong>：QPS 键为 {@code 前缀 + 维度 + ":" + epochSecond / windowSeconds}，
 * 窗口按 epoch 整除对齐，窗口切换即换键，因此 {@code window-seconds=N} 表示「每 N 秒最多
 * qps 次」，默认 {@code N=1} 即 QPS。TTL 取「本窗口剩余秒数 + 1」，不会把旧窗口的计数带入新窗口。
 * 日配额键按 {@link Clock} 的时区取自然日，TTL 到次日零点。</p>
 *
 * <p><strong>响应契约</strong>：超限返回真 HTTP 429（{@code CONTRACT.md} 登记的入口拒绝），响应体
 * 与其它网关响应同为 {@link R} 信封（{@code code=429}），不再手工拼装只有三个字段的 Map。</p>
 *
 * <p><strong>Redis 异常 fail-open</strong>：计数失败时本次放行，但必须留下日志——同一失败原因的
 * WARN 按 {@value #FAILURE_WARN_INTERVAL_MILLIS} 毫秒降频（Redis 故障时每个请求一条带栈日志会
 * 打爆磁盘），降频窗口内的明细降到 DEBUG。超限拒绝的 WARN 同样降频：拒绝是客户端可任意触发的，
 * 不降频等于给外部提供日志放大器。</p>
 *
 * @author wenbin
 * @since 2026-10-02
 */
public class AttributeRateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AttributeRateLimitGlobalFilter.class);

    /** 同一原因的 WARN 降频窗口（毫秒）。 */
    private static final long FAILURE_WARN_INTERVAL_MILLIS = 5_000L;

    /** 序列化失败时的兜底响应体：字段与 {@link R} 一致，仅时间戳缺失（不影响 code 判定）。 */
    private static final byte[] FALLBACK_BODY = ("{\"code\":" + GlobalErrorCode.TOO_MANY_REQUESTS.getCode()
        + ",\"message\":\"" + GlobalErrorCode.TOO_MANY_REQUESTS.getMessage()
        + "\",\"data\":null,\"success\":false,\"timestamp\":null}").getBytes(StandardCharsets.UTF_8);

    private final ReactiveStringRedisTemplate redis;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final DefaultRedisScript<Long> counterScript;
    private final AtomicLong lastRedisWarnAt = new AtomicLong();
    private final AtomicLong lastRejectWarnAt = new AtomicLong();

    /**
     * 以系统默认时区构造（对齐既有装配签名）。
     *
     * @param redis        响应式 Redis 模板
     * @param properties   限流配置
     * @param objectMapper 响应序列化器
     */
    public AttributeRateLimitGlobalFilter(ReactiveStringRedisTemplate redis,
                                          RateLimitProperties properties,
                                          ObjectMapper objectMapper) {
        this(redis, properties, objectMapper, Clock.systemDefaultZone());
    }

    /**
     * 指定时钟构造（日配额的自然日边界、窗口序号都以该时钟为准，便于测试窗口切换）。
     *
     * @param redis        响应式 Redis 模板
     * @param properties   限流配置
     * @param objectMapper 响应序列化器
     * @param clock        时钟
     */
    public AttributeRateLimitGlobalFilter(ReactiveStringRedisTemplate redis,
                                          RateLimitProperties properties,
                                          ObjectMapper objectMapper,
                                          Clock clock) {
        this.redis = redis;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.counterScript = new DefaultRedisScript<>();
        this.counterScript.setResultType(Long.class);
        this.counterScript.setScriptSource(new ResourceScriptSource(
            new ClassPathResource("META-INF/ypbin/gateway_rate_limit.lua")));
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
        if (qpsLimit <= 0 && quotaLimit <= 0) {
            // 两维都不限：不碰 Redis，也不给 Redis 制造无用写入
            return chain.filter(exchange);
        }
        Instant now = clock.instant();
        Mono<Long> qpsCount = qpsLimit <= 0
            ? Mono.just(0L)
            : incr(qpsKey(dimension, now), qpsTtlSeconds(now));
        if (quotaLimit <= 0) {
            return qpsCount.flatMap(count ->
                decide(exchange, chain, dimension, count, qpsLimit, 0L, quotaLimit));
        }
        return qpsCount.flatMap(count -> incr(quotaKey(dimension, now), quotaTtlSeconds(now))
            .flatMap(quotaCount -> decide(exchange, chain, dimension, count, qpsLimit, quotaCount, quotaLimit)));
    }

    /**
     * QPS 固定窗口键：窗口序号 = {@code epochSecond / windowSeconds}，窗口切换即换键。
     *
     * @param dimension 维度键（如 API Key）
     * @param now       当前时刻
     * @return Redis 计数键
     */
    String qpsKey(String dimension, Instant now) {
        return properties.getQpsKeyPrefix() + dimension + ":" + Math.floorDiv(now.getEpochSecond(), windowSeconds());
    }

    /**
     * QPS 计数键 TTL：本窗口剩余秒数 + 1 秒冗余，保证窗口结束后旧键自然消失。
     *
     * @param now 当前时刻
     * @return TTL 秒数（>= 1）
     */
    long qpsTtlSeconds(Instant now) {
        long window = windowSeconds();
        return window - Math.floorMod(now.getEpochSecond(), window) + 1L;
    }

    /**
     * 日配额键：按 {@link Clock} 的时区取自然日。
     *
     * @param dimension 维度键
     * @param now       当前时刻
     * @return Redis 计数键
     */
    String quotaKey(String dimension, Instant now) {
        return properties.getQuotaKeyPrefix() + dimension + ":" + LocalDate.ofInstant(now, clock.getZone());
    }

    /**
     * 日配额键 TTL：到次日零点（+1 秒冗余，避免临界点提前过期）。
     *
     * @param now 当前时刻
     * @return TTL 秒数（>= 1）
     */
    long quotaTtlSeconds(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, clock.getZone());
        Instant nextMidnight = today.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        return Duration.between(now, nextMidnight).getSeconds() + 1L;
    }

    private Mono<Void> decide(ServerWebExchange exchange, GatewayFilterChain chain, String dimension,
                              long qpsCount, int qpsLimit, long quotaCount, int quotaLimit) {
        FixedWindowRateLimit qpsDecision = FixedWindowRateLimit.forCount(qpsCount, qpsLimit);
        FixedWindowRateLimit quotaDecision = FixedWindowRateLimit.forCount(quotaCount, quotaLimit);
        if (qpsDecision.allowed() && quotaDecision.allowed()) {
            return chain.filter(exchange);
        }
        warnRejected(dimension, qpsCount, qpsLimit, quotaCount, quotaLimit);
        // 两维同时超限时只报先判定的 QPS 维：调用方拿到一条可执行的提示即可
        String message = !qpsDecision.allowed()
            ? "请求过于频繁，请稍后重试（每 " + windowSeconds() + " 秒最多 " + qpsLimit + " 次）"
            : "已超过当日调用配额，请稍后重试";
        return reject(exchange, message);
    }

    private Mono<Long> incr(String key, long ttlSeconds) {
        return redis.execute(counterScript, List.of(key), String.valueOf(Math.max(1L, ttlSeconds)))
            .next()
            .switchIfEmpty(Mono.fromSupplier(() -> {
                warnRedisFailure("[ypbin-starter] 限流计数脚本无返回值，本次按不限放行：key={}", key);
                return -1L;
            }))
            .onErrorResume(ex -> {
                warnRedisFailure("[ypbin-starter] 限流计数失败，本次按不限放行：key={}", key, ex);
                return Mono.just(-1L);
            });
    }

    private static int attrInt(ServerWebExchange exchange, String name, int def) {
        Object value = exchange.getAttribute(name);
        return value instanceof Number number ? number.intValue() : def;
    }

    private long windowSeconds() {
        return Math.max(1, properties.getWindowSeconds());
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        byte[] body = toJsonBytes(R.fail(GlobalErrorCode.TOO_MANY_REQUESTS.getCode(), message));
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    private byte[] toJsonBytes(R<Void> body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (Exception ex) {
            log.error("[ypbin-starter] 限流响应序列化失败，改用静态 JSON", ex);
            return FALLBACK_BODY;
        }
    }

    private void warnRejected(String dimension, long qpsCount, int qpsLimit, long quotaCount, int quotaLimit) {
        // 维度键来自上游 attributes，可能含换行等控制字符，落日志前统一清洗（防日志注入）
        String safeDimension = LogSanitizer.sanitize(dimension);
        if (throttled(lastRejectWarnAt)) {
            log.warn("[ypbin-starter] rate limited: dimension={} qps={}/{} quota={}/{} window={}s",
                safeDimension, qpsCount, qpsLimit, quotaCount, quotaLimit, windowSeconds());
        } else if (log.isDebugEnabled()) {
            log.debug("[ypbin-starter] rate limited: dimension={} qps={}/{} quota={}/{} window={}s",
                safeDimension, qpsCount, qpsLimit, quotaCount, quotaLimit, windowSeconds());
        }
    }

    private void warnRedisFailure(String message, Object... args) {
        if (throttled(lastRedisWarnAt)) {
            log.warn(message, args);
        } else if (log.isDebugEnabled()) {
            log.debug(message, args);
        }
    }

    private static boolean throttled(AtomicLong lastWarnAt) {
        long now = System.currentTimeMillis();
        long last = lastWarnAt.get();
        return now - last >= FAILURE_WARN_INTERVAL_MILLIS && lastWarnAt.compareAndSet(last, now);
    }

    @Override
    public int getOrder() {
        return properties.getOrder();
    }
}

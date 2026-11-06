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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

/**
 * 属性驱动限流过滤器用例（Redis 一律 mock，不起真实连接）。
 *
 * @author wenbin
 * @since 2026-10-02
 */
class AttributeRateLimitGlobalFilterTest {

    private static final Instant NOW = Instant.parse("2026-11-06T12:00:05Z");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ReactiveStringRedisTemplate redis;

    private RateLimitProperties properties;

    private AttributeRateLimitGlobalFilter filter;

    @BeforeEach
    void setUp() {
        redis = mock(ReactiveStringRedisTemplate.class);
        properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setPathPrefixes(List.of("/iot/open-api/v1/"));
        filter = new AttributeRateLimitGlobalFilter(redis, properties, MAPPER,
            Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** 依次返回 QPS 计数与日配额计数（只传一个 = 仅 QPS 维计数）。 */
    @SafeVarargs
    private void stubCounts(Long... counts) {
        var stubber = doReturn(Flux.just(counts[0]));
        for (int i = 1; i < counts.length; i++) {
            stubber = stubber.doReturn(Flux.just(counts[i]));
        }
        stubber.when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    private MockServerWebExchange exchange(String path, String dimension, int qps, int quota) {
        MockServerWebExchange exchange =
            MockServerWebExchange.from(MockServerHttpRequest.get(path));
        if (dimension != null) {
            exchange.getAttributes().put(properties.getKeyAttribute(), dimension);
            exchange.getAttributes().put(properties.getQpsAttribute(), qps);
            exchange.getAttributes().put(properties.getQuotaAttribute(), quota);
        }
        return exchange;
    }

    private static GatewayFilterChain passingChain() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        return chain;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> responseBody(MockServerWebExchange exchange) {
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).isNotNull();
        return (Map<String, Object>) MAPPER.readValue(body, Map.class);
    }

    @Test
    @DisplayName("非生效路径直接放行（不碰 Redis）")
    void nonMatchingPathMustPassThrough() {
        GatewayFilterChain chain = passingChain();

        filter.filter(exchange("/iot/devices", "ak_1", 1, 100), chain)
            .as(StepVerifier::create)
            .verifyComplete();

        verify(chain).filter(any());
        verifyNoInteractions(redis);
    }

    @Test
    @DisplayName("无维度键直接放行（上游未供给不拦）")
    void missingDimensionMustPassThrough() {
        GatewayFilterChain chain = passingChain();

        filter.filter(exchange("/iot/open-api/v1/devices", null, 1, 100), chain)
            .as(StepVerifier::create)
            .verifyComplete();

        verify(chain).filter(any());
        verifyNoInteractions(redis);
    }

    @Test
    @DisplayName("两维都不限时不碰 Redis")
    void unlimitedDimensionsSkipRedis() {
        GatewayFilterChain chain = passingChain();

        filter.filter(exchange("/iot/open-api/v1/devices", "ak_1", 0, 0), chain)
            .as(StepVerifier::create)
            .verifyComplete();

        verify(chain).filter(any());
        verifyNoInteractions(redis);
    }

    @Test
    @DisplayName("配额内放行")
    void withinQuotaMustPass() {
        stubCounts(1L, 1L);
        GatewayFilterChain chain = passingChain();

        filter.filter(exchange("/iot/open-api/v1/devices", "ak_1", 10, 100), chain)
            .as(StepVerifier::create)
            .verifyComplete();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("只限日配额时不写 QPS 计数键")
    void quotaOnlyMustIncrementQuotaOnly() {
        stubCounts(1L);
        GatewayFilterChain chain = passingChain();

        filter.filter(exchange("/iot/open-api/v1/devices", "ak_1", 0, 100), chain)
            .as(StepVerifier::create)
            .verifyComplete();

        verify(chain).filter(any());
        verify(redis, times(1)).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    @DisplayName("超 QPS 返回真 429，响应体是完整 R 信封且不转发")
    void overQpsMustRejectWith429() {
        stubCounts(2L, 1L);
        GatewayFilterChain chain = passingChain();

        MockServerWebExchange exchange = exchange("/iot/open-api/v1/devices", "ak_1", 1, 100);
        filter.filter(exchange, chain).as(StepVerifier::create).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        Map<String, Object> body = responseBody(exchange);
        assertThat(body).containsKeys("code", "message", "data", "success", "timestamp");
        assertThat(body.get("code")).isEqualTo(429);
        assertThat(body.get("success")).isEqualTo(false);
        assertThat((String) body.get("message")).contains("每 1 秒最多 1 次");
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("超日配额返回 429 并提示配额")
    void overQuotaMustReject() {
        stubCounts(1L, 101L);
        GatewayFilterChain chain = passingChain();

        MockServerWebExchange exchange = exchange("/iot/open-api/v1/devices", "ak_1", 10, 100);
        filter.filter(exchange, chain).as(StepVerifier::create).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat((String) responseBody(exchange).get("message")).contains("当日调用配额");
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("attributes 里用 Long 表示配额同样生效")
    void longAttributeValuesAreAccepted() {
        stubCounts(2L, 1L);
        GatewayFilterChain chain = passingChain();

        MockServerWebExchange exchange =
            MockServerWebExchange.from(MockServerHttpRequest.get("/iot/open-api/v1/devices"));
        exchange.getAttributes().put(properties.getKeyAttribute(), "ak_1");
        exchange.getAttributes().put(properties.getQpsAttribute(), 1L);
        exchange.getAttributes().put(properties.getQuotaAttribute(), 100L);
        filter.filter(exchange, chain).as(StepVerifier::create).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("Redis 异常 fail-open（仅记日志，不阻断业务）")
    void redisFailureMustFailOpen() {
        doReturn(Flux.error(new RuntimeException("down")))
            .when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        GatewayFilterChain chain = passingChain();

        filter.filter(exchange("/iot/open-api/v1/devices", "ak_1", 1, 100), chain)
            .as(StepVerifier::create)
            .verifyComplete();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("Redis 持续失败时 WARN 在降频窗口内只出现一次")
    void redisFailureWarnsOncePerWindow() {
        doReturn(Flux.error(new IllegalStateException("down")))
            .when(redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
        GatewayFilterChain chain = passingChain();
        Logger logger = (Logger) LoggerFactory.getLogger(AttributeRateLimitGlobalFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            for (int i = 0; i < 50; i++) {
                filter.filter(exchange("/iot/open-api/v1/devices", "ak_1", 1, 100), chain)
                    .as(StepVerifier::create)
                    .verifyComplete();
            }
        } finally {
            logger.detachAppender(appender);
        }

        long warnCount = appender.list.stream().filter(event -> event.getLevel() == Level.WARN).count();
        assertThat(warnCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("连续超限的拒绝日志同样降频（防止日志放大）")
    void rejectionLogIsThrottled() {
        stubCounts(2L, 1L);
        Logger logger = (Logger) LoggerFactory.getLogger(AttributeRateLimitGlobalFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            for (int i = 0; i < 50; i++) {
                filter.filter(exchange("/iot/open-api/v1/devices", "ak_1", 1, 100), passingChain())
                    .as(StepVerifier::create)
                    .verifyComplete();
            }
        } finally {
            logger.detachAppender(appender);
        }

        long warnCount = appender.list.stream().filter(event -> event.getLevel() == Level.WARN).count();
        assertThat(warnCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("窗口按 epoch 整除对齐，窗口切换即换键")
    void qpsKeyUsesAlignedWindow() {
        properties.setWindowSeconds(5);
        properties.setQpsKeyPrefix("qps:");

        assertThat(filter.qpsKey("ak_1", Instant.ofEpochSecond(0))).isEqualTo("qps:ak_1:0");
        assertThat(filter.qpsKey("ak_1", Instant.ofEpochSecond(4))).isEqualTo("qps:ak_1:0");
        assertThat(filter.qpsKey("ak_1", Instant.ofEpochSecond(5))).isEqualTo("qps:ak_1:1");
        assertThat(filter.qpsTtlSeconds(Instant.ofEpochSecond(0))).isEqualTo(6L);
        assertThat(filter.qpsTtlSeconds(Instant.ofEpochSecond(4))).isEqualTo(2L);
    }

    @Test
    @DisplayName("窗口长度非法时按 1 秒处理")
    void invalidWindowFallsBackToOneSecond() {
        properties.setWindowSeconds(0);

        assertThat(filter.qpsTtlSeconds(Instant.ofEpochSecond(7))).isEqualTo(2L);
    }

    @Test
    @DisplayName("日配额键按时钟时区取自然日，TTL 到次日零点")
    void quotaKeyUsesClockZoneDay() {
        properties.setQuotaKeyPrefix("quota:");

        assertThat(filter.quotaKey("ak_1", NOW)).isEqualTo("quota:ak_1:2026-11-06");
        // NOW = 12:00:05Z，距次日零点 43195 秒，+1 秒冗余
        assertThat(filter.quotaTtlSeconds(NOW)).isEqualTo(43196L);
    }

    @Test
    @DisplayName("序列化失败时回退为同结构的静态 JSON")
    void serializationFailureFallsBackToStaticJson() {
        ObjectMapper brokenMapper = mock(ObjectMapper.class);
        when(brokenMapper.writeValueAsBytes(any())).thenThrow(new IllegalStateException("boom"));
        AttributeRateLimitGlobalFilter brokenFilter =
            new AttributeRateLimitGlobalFilter(redis, properties, brokenMapper, Clock.fixed(NOW, ZoneOffset.UTC));
        stubCounts(2L, 1L);

        MockServerWebExchange exchange = exchange("/iot/open-api/v1/devices", "ak_1", 1, 100);
        brokenFilter.filter(exchange, passingChain()).as(StepVerifier::create).verifyComplete();

        Map<String, Object> body = responseBody(exchange);
        assertThat(body.get("code")).isEqualTo(429);
        assertThat(body.get("success")).isEqualTo(false);
    }
}

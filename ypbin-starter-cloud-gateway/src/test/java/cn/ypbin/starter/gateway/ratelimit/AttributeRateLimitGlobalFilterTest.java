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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
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

    private ReactiveStringRedisTemplate redis;

    private ReactiveValueOperations<String, String> valueOps;

    private RateLimitProperties properties;

    private AttributeRateLimitGlobalFilter filter;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(ReactiveStringRedisTemplate.class);
        valueOps = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        properties = new RateLimitProperties();
        properties.setEnabled(true);
        properties.setPathPrefixes(List.of("/iot/open-api/v1/"));
        filter = new AttributeRateLimitGlobalFilter(redis, properties, new ObjectMapper());
    }

    private ServerWebExchange exchange(String path, String dimension, int qps, int quota) {
        MockServerWebExchange exchange =
            MockServerWebExchange.from(MockServerHttpRequest.get(path));
        if (dimension != null) {
            exchange.getAttributes().put(properties.getKeyAttribute(), dimension);
            exchange.getAttributes().put(properties.getQpsAttribute(), qps);
            exchange.getAttributes().put(properties.getQuotaAttribute(), quota);
        }
        return exchange;
    }

    @Test
    @DisplayName("非生效路径直接放行（不碰 Redis）")
    void nonMatchingPathMustPassThrough() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange("/iot/devices", "ak_1", 1, 100), chain)
            .as(StepVerifier::create)
            .verifyComplete();

        verify(chain).filter(any());
        verify(redis, never()).opsForValue();
    }

    @Test
    @DisplayName("无维度键直接放行（上游未供给不拦）")
    void missingDimensionMustPassThrough() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange("/iot/open-api/v1/devices", null, 1, 100), chain)
            .as(StepVerifier::create)
            .verifyComplete();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("配额内放行，计数器各 +1")
    void withinQuotaMustPass() {
        when(valueOps.increment(anyString())).thenReturn(Mono.just(1L));
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange("/iot/open-api/v1/devices", "ak_1", 10, 100), chain)
            .as(StepVerifier::create)
            .verifyComplete();

        verify(chain).filter(any());
    }

    @Test
    @DisplayName("超 QPS 返回真 429 且不继续转发")
    void overQpsMustRejectWith429() {
        when(valueOps.increment(anyString())).thenReturn(Mono.just(2L));
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        ServerWebExchange exchange = exchange("/iot/open-api/v1/devices", "ak_1", 1, 100);
        filter.filter(exchange, chain).as(StepVerifier::create).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("Redis 异常 fail-open（仅记日志，不阻断业务）")
    void redisFailureMustFailOpen() {
        when(valueOps.increment(anyString())).thenReturn(Mono.error(new RuntimeException("down")));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange("/iot/open-api/v1/devices", "ak_1", 1, 100), chain)
            .as(StepVerifier::create)
            .verifyComplete();

        verify(chain).filter(any());
    }
}

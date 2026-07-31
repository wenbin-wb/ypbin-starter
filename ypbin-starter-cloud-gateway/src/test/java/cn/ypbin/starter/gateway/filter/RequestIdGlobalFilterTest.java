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
package cn.ypbin.starter.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.test.StepVerifier;

/**
 * {@link RequestIdGlobalFilter} 单元测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class RequestIdGlobalFilterTest {

    @Test
    void shouldGenerateRequestIdWhenMissing() {
        RequestIdGlobalFilter filter = new RequestIdGlobalFilter("X-Request-Id");
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));

        filter.filter(exchange, e -> {
            String id = e.getRequest().getHeaders().getFirst("X-Request-Id");
            assertThat(id).isNotBlank();
            String responseId = e.getResponse().getHeaders().getFirst("X-Request-Id");
            assertThat(responseId).isNotBlank();
            return reactor.core.publisher.Mono.empty();
        }).as(StepVerifier::create).verifyComplete();
    }

    @Test
    void shouldReuseExistingRequestId() {
        RequestIdGlobalFilter filter = new RequestIdGlobalFilter("X-Request-Id");
        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/test").header("X-Request-Id", "existing-id"));

        filter.filter(exchange, e -> {
            assertThat(e.getRequest().getHeaders().getFirst("X-Request-Id")).isEqualTo("existing-id");
            return reactor.core.publisher.Mono.empty();
        }).as(StepVerifier::create).verifyComplete();
    }

    @Test
    void shouldWriteResponseHeader() {
        RequestIdGlobalFilter filter = new RequestIdGlobalFilter("X-Request-Id");
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));

        filter.filter(exchange, e -> {
            assertThat(e.getResponse().getHeaders().getFirst("X-Request-Id")).isNotBlank();
            return reactor.core.publisher.Mono.empty();
        }).as(StepVerifier::create).verifyComplete();
    }

    @Test
    void shouldUseCustomHeaderName() {
        RequestIdGlobalFilter filter = new RequestIdGlobalFilter("X-Trace-Id");
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test"));

        filter.filter(exchange, e -> {
            assertThat(e.getRequest().getHeaders().getFirst("X-Trace-Id")).isNotBlank();
            assertThat(e.getRequest().getHeaders().getFirst("X-Request-Id")).isNull();
            return reactor.core.publisher.Mono.empty();
        }).as(StepVerifier::create).verifyComplete();
    }

    @Test
    void shouldHaveHighestPrecedenceOrder() {
        RequestIdGlobalFilter filter = new RequestIdGlobalFilter("X-Request-Id");

        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}

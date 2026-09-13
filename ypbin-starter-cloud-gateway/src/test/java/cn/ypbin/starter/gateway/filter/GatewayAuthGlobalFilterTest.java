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

import cn.ypbin.starter.gateway.auth.GatewayAuthProvider;
import cn.ypbin.starter.gateway.auth.GatewayAuthResult;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link GatewayAuthGlobalFilter} 身份头签发测试。
 *
 * <p>重点验证「来源标记」签发：配置了 trusted-source-token 时，网关在写入身份头的同时写出标记头，
 * 供下游判定身份头来源可信；未配置时不写标记（保持兼容，下游按原有行为透传）。</p>
 *
 * @author wenbin
 * @since 2026-09-13
 */
class GatewayAuthGlobalFilterTest {

    private static final GatewayAuthProvider PROVIDER = exchange -> Mono.just(
        GatewayAuthResult.success(Map.of("X-User-Id", "42")));

    private static ServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/demo"));
    }

    @Test
    void shouldSignTrustedSourceHeaderWhenConfigured() {
        GatewayAuthGlobalFilter filter = new GatewayAuthGlobalFilter(PROVIDER,
            JsonMapper.builder().build(), java.util.List.of(), "X-Gateway-Signed", "s3cret");

        filter.filter(exchange(), e -> {
            assertThat(e.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("42");
            assertThat(e.getRequest().getHeaders().getFirst("X-Gateway-Signed")).isEqualTo("s3cret");
            return Mono.empty();
        }).as(StepVerifier::create).verifyComplete();
    }

    @Test
    void shouldNotSignTrustedSourceHeaderWhenNotConfigured() {
        GatewayAuthGlobalFilter filter = new GatewayAuthGlobalFilter(PROVIDER,
            JsonMapper.builder().build(), java.util.List.of(), "X-Gateway-Signed", "");

        filter.filter(exchange(), e -> {
            assertThat(e.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("42");
            assertThat(e.getRequest().getHeaders().getFirst("X-Gateway-Signed")).isNull();
            return Mono.empty();
        }).as(StepVerifier::create).verifyComplete();
    }

    @Test
    void shouldSkipExcludedPathWithoutAuthenticating() {
        GatewayAuthProvider failing = exchange -> {
            throw new AssertionError("白名单路径不应触发认证");
        };
        GatewayAuthGlobalFilter filter = new GatewayAuthGlobalFilter(failing,
            JsonMapper.builder().build(), java.util.List.of("/actuator/**"), "X-Gateway-Signed", "s3cret");

        ServerWebExchange exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/actuator/health"));

        filter.filter(exchange, e -> Mono.empty()).as(StepVerifier::create).verifyComplete();
    }
}

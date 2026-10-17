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
package cn.ypbin.starter.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 网关真运行时端到端测试。
 *
 * <p>不依赖 Nacos/Docker：用 JDK 内置 {@link HttpServer} 作下游桩，真启动 Spring Cloud Gateway
 * 上下文，通过 {@link WebTestClient} 发真实 HTTP 请求穿过整条网关链路，验证静态路由转发、
 * 请求 ID 透传与回写、入口身份头清洗、以及未知路由的统一 {@code R} JSON 响应。</p>
 *
 * <p>下游端口在 {@link #startDownstream()} 里动态分配后，通过 {@code spring.cloud.gateway.server.webflux.routes}
 * 注入路由（Spring Cloud Gateway 4.1+ 新前缀），顺带验证该前缀在当前锁定版本下生效。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.main.web-application-type=reactive",
        "spring.cloud.nacos.config.import-check.enabled=false",
        "spring.cloud.gateway.server.webflux.routes[0].id=downstream",
        "spring.cloud.gateway.server.webflux.routes[0].uri=http://localhost:${ypbin.test.downstream-port}",
        "spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/api/**",
        // 用真实注册的 /internal/** 路由作对照：若网关层没有阻断，该请求会被真的转发到下游桩
        "spring.cloud.gateway.server.webflux.routes[1].id=internal-route",
        "spring.cloud.gateway.server.webflux.routes[1].uri=http://localhost:${ypbin.test.downstream-port}",
        "spring.cloud.gateway.server.webflux.routes[1].predicates[0]=Path=/internal/**",
        "ypbin.gateway.header-sanitize.headers[0]=X-User-Id"
    })
class GatewayE2ETest {

    private static HttpServer downstream;

    /** 下游收到的最近一次请求头快照，供断言透传/清洗行为 */
    private static final Map<String, String> RECEIVED_HEADERS = new ConcurrentHashMap<>();

    /** 下游 {@code /internal/**} 是否被真实访问过（证明网关层已阻断、未转发） */
    private static final AtomicBoolean DOWNSTREAM_INTERNAL_HIT = new AtomicBoolean();

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        this.webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost:" + port)
            .build();
    }

    @BeforeAll
    static void startDownstream() throws IOException {
        downstream = HttpServer.create(new InetSocketAddress(0), 0);
        downstream.createContext("/api/echo", exchange -> {
            RECEIVED_HEADERS.clear();
            exchange.getRequestHeaders().forEach((k, v) -> {
                if (!v.isEmpty()) {
                    RECEIVED_HEADERS.put(k.toLowerCase(), v.getFirst());
                }
            });
            byte[] body = "downstream-ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        downstream.createContext("/internal/", exchange -> {
            DOWNSTREAM_INTERNAL_HIT.set(true);
            byte[] body = "should-not-reach-downstream".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        downstream.start();
        System.setProperty("ypbin.test.downstream-port", String.valueOf(downstream.getAddress().getPort()));
    }

    @AfterAll
    static void stopDownstream() {
        if (downstream != null) {
            downstream.stop(0);
        }
        System.clearProperty("ypbin.test.downstream-port");
    }

    @Test
    void shouldRouteThroughGatewayAndPropagateRequestId() {
        webTestClient.get().uri("/api/echo")
            .header("X-Request-Id", "e2e-req-1")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals("X-Request-Id", "e2e-req-1")
            .expectBody(String.class).isEqualTo("downstream-ok");

        // 下游确实收到了透传的请求 ID
        assertThat(RECEIVED_HEADERS.get("x-request-id")).isEqualTo("e2e-req-1");
    }

    @Test
    void shouldGenerateRequestIdWhenAbsent() {
        webTestClient.get().uri("/api/echo")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().exists("X-Request-Id");

        assertThat(RECEIVED_HEADERS.get("x-request-id")).isNotBlank();
    }

    @Test
    void shouldStripForgedIdentityHeader() {
        webTestClient.get().uri("/api/echo")
            .header("X-User-Id", "999")
            .exchange()
            .expectStatus().isOk();

        // 客户端伪造的身份头在入口被清洗，不会到达下游
        assertThat(RECEIVED_HEADERS.get("x-user-id")).isNull();
    }

    @Test
    void shouldReturnUnifiedJsonForUnknownRoute() {
        webTestClient.get().uri("/no-such-route")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(false)
            .jsonPath("$.code").isEqualTo(404);
    }

    /**
     * 内部端点通配阻断的真运行时验证（GitHub issue #96）。
     *
     * <p>对照设计：{@code /internal/**} 是<b>已注册的真实路由</b>（指向下游桩），因此本用例同时证明
     * 了两件事——① 阻断发生在转发之前（下游桩的 {@code /internal/} 上下文从未被访问）；② 命中后
     * 返回的是不含路径细节的统一 404 信封。</p>
     */
    @Test
    void shouldBlockInternalPathAtGatewayWithoutForwarding() {
        DOWNSTREAM_INTERNAL_HIT.set(false);

        webTestClient.get().uri("/internal/secret")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody()
            .jsonPath("$.success").isEqualTo(false)
            .jsonPath("$.code").isEqualTo(404);

        assertThat(DOWNSTREAM_INTERNAL_HIT)
            .as("被阻断的内部路径请求绝不能被转发到下游")
            .isFalse();
    }

    /**
     * issue #96 的核心验收：内部端点阻断的 404 与未知路由的 404 对调用方<b>不可区分</b>。
     *
     * <p>两者在真实运行时走完全不同的代码路径（未知路由 → {@code GatewayExceptionHandler}；
     * 内部路径 → {@code InternalPathBlockGlobalFilter}），本用例取回两个响应体、剔除动态
     * {@code timestamp} 后逐字段比较，确保文案与字段结构不再漂移。HTTP 状态码差异
     * （未知路由沿用既有 HTTP 200 约定、内部路径按 issue #96 要求返回 404）是刻意保留的——issue 只要求
     * 阻断侧为 404，并未要求改变未知路由既有契约，故本用例只比较响应体。</p>
     */
    @Test
    void blockedInternalPathShouldBeIndistinguishableFromUnknownRoute() {
        byte[] unknownRouteBody = webTestClient.get().uri("/no-such-route")
            .exchange()
            .expectStatus().isOk()
            .expectBody().returnResult().getResponseBody();

        byte[] blockedBody = webTestClient.get().uri("/internal/secret")
            .exchange()
            .expectStatus().isNotFound()
            .expectBody().returnResult().getResponseBody();

        assertThat(envelopeWithoutTimestamp(blockedBody))
            .as("两个来源的 404 响应体必须逐字段一致（除动态 timestamp），否则内部端点存在性对外可探测")
            .isEqualTo(envelopeWithoutTimestamp(unknownRouteBody));
    }

    /** 解析响应体并剔除动态 {@code timestamp}，用于「除时间戳外逐字段一致」的比较。 */
    private static ObjectNode envelopeWithoutTimestamp(byte[] body) {
        assertThat(body).as("404 信封必须返回响应体").isNotNull();
        ObjectNode envelope = (ObjectNode) new ObjectMapper().readTree(body);
        envelope.remove("timestamp");
        return envelope;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
    }
}

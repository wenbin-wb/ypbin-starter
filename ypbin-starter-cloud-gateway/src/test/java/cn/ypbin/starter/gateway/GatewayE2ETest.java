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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

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
        "ypbin.gateway.header-sanitize.headers[0]=X-User-Id"
    })
class GatewayE2ETest {

    private static HttpServer downstream;

    /** 下游收到的最近一次请求头快照，供断言透传/清洗行为 */
    private static final Map<String, String> RECEIVED_HEADERS = new ConcurrentHashMap<>();

    @Autowired
    private WebTestClient webTestClient;

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

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
    }
}

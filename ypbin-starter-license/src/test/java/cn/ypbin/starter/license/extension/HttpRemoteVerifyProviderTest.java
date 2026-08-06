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
package cn.ypbin.starter.license.extension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.license.core.LicenseContent;
import cn.ypbin.starter.license.exception.LicenseException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link HttpRemoteVerifyProvider} 联机校验单元测试：验证「仅服务端明确返回无效才阻断，网络/服务异常
 * 一律放行」的容忍策略。
 *
 * @author wenbin
 * @since 2026-08-06
 */
class HttpRemoteVerifyProviderTest {

    private HttpServer server;
    private volatile int status = 200;
    private volatile String responseBody = "{\"data\":{\"valid\":true,\"reason\":\"ok\"}}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/open/license/verify", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private HttpRemoteVerifyProvider provider() {
        return new HttpRemoteVerifyProvider("http://localhost:" + server.getAddress().getPort(),
            "test-token", Duration.ofSeconds(2));
    }

    private LicenseContent content() {
        return new LicenseContent("LIC-0001", "Demo", null, List.of(), null,
            LocalDateTime.now(), null, null, 0, List.of(), Map.of(), Map.of());
    }

    @Test
    void verify_shouldPassWhenServiceReportsValid() {
        assertThatCode(() -> provider().verify(content(), "f1")).doesNotThrowAnyException();
    }

    @Test
    void verify_shouldRejectWhenServiceReportsInvalid() {
        responseBody = "{\"data\":{\"valid\":false,\"reason\":\"授权已被吊销\"}}";

        assertThatThrownBy(() -> provider().verify(content(), "f1"))
            .isInstanceOf(LicenseException.class);
    }

    @Test
    void verify_shouldAllowWhenServiceReturnsError() {
        status = 500;

        assertThatCode(() -> provider().verify(content(), "f1")).doesNotThrowAnyException();
    }

    @Test
    void verify_shouldAllowWhenServiceUnreachable() {
        server.stop(0);

        assertThatCode(() -> provider().verify(content(), "f1")).doesNotThrowAnyException();
    }

    @Test
    void verify_shouldAllowWhenContentAbsent() {
        assertThatCode(() -> provider().verify(null, "f1")).doesNotThrowAnyException();
    }
}

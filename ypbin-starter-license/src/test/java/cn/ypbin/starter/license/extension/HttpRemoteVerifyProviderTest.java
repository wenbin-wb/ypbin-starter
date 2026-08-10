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

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link HttpRemoteVerifyProvider} 联机校验单元测试：验证「仅服务端明确返回无效才阻断，其余一切放行但
 * 不明确有效」的三桶裁决策略，以及放行窗口/退避升级/单飞的防打爆行为。
 *
 * @author wenbin
 * @since 2026-08-06
 */
class HttpRemoteVerifyProviderTest {

    private HttpServer server;
    private volatile int status = 200;
    private volatile String responseBody = "{\"data\":{\"valid\":true,\"reason\":\"ok\"}}";
    private volatile int requestCount = 0;

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
        requestCount++;
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private HttpRemoteVerifyProvider provider() {
        return provider(3600, 60, 5, 300);
    }

    private HttpRemoteVerifyProvider provider(long cacheSeconds) {
        return provider(cacheSeconds, 60, 5, 300);
    }

    private HttpRemoteVerifyProvider provider(long cacheSeconds, long failOpenCacheSeconds, int failOpenThreshold,
        long failOpenBackoffSeconds) {
        return provider(cacheSeconds, failOpenCacheSeconds, failOpenThreshold, failOpenBackoffSeconds,
            RemoteFailurePolicy.FAIL_OPEN_WITH_WARNING);
    }

    private HttpRemoteVerifyProvider provider(long cacheSeconds, long failOpenCacheSeconds, int failOpenThreshold,
        long failOpenBackoffSeconds, RemoteFailurePolicy failurePolicy) {
        return new HttpRemoteVerifyProvider("http://localhost:" + server.getAddress().getPort(),
            "test-access-key", "test-secret-key", Duration.ofSeconds(2), cacheSeconds, failOpenCacheSeconds,
            failOpenThreshold, failOpenBackoffSeconds, failurePolicy);
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
    void verify_shouldRejectErrorWhenFailClosed() {
        status = 500;
        HttpRemoteVerifyProvider p = provider(3600, 60, 5, 300, RemoteFailurePolicy.FAIL_CLOSED);

        assertThatThrownBy(() -> p.verify(content(), "f1"))
            .isInstanceOf(LicenseException.class);
    }

    @Test
    void verify_shouldRejectUnreachableWhenFailClosed() {
        server.stop(0);
        HttpRemoteVerifyProvider p = provider(3600, 60, 5, 300, RemoteFailurePolicy.FAIL_CLOSED);

        assertThatThrownBy(() -> p.verify(content(), "f1"))
            .isInstanceOf(LicenseException.class);
    }

    @Test
    void verify_shouldRejectMalformedResponseWhenFailClosed() {
        responseBody = "{not-json";
        HttpRemoteVerifyProvider p = provider(3600, 60, 5, 300, RemoteFailurePolicy.FAIL_CLOSED);

        assertThatThrownBy(() -> p.verify(content(), "f1"))
            .isInstanceOf(LicenseException.class);
    }

    @Test
    void verify_shouldAllowWhenContentAbsent() {
        assertThatCode(() -> provider().verify(null, "f1")).doesNotThrowAnyException();
    }

    @Test
    void verify_shouldTreatMissingValidFieldAsFailOpenNotValid() {
        // valid 字段缺失：不是明确裁决，绝不能当作有效缓存住
        responseBody = "{\"data\":{\"reason\":\"unknown\"}}";
        HttpRemoteVerifyProvider p = provider(3600, 0, 5, 300);
        assertThatCode(() -> p.verify(content(), "f1")).doesNotThrowAnyException();

        // 若上一步被误判为「明确有效」并缓存，这里就不会真正联机、也不会抛异常
        responseBody = "{\"data\":{\"valid\":false,\"reason\":\"revoked\"}}";
        assertThatThrownBy(() -> p.verify(content(), "f1")).isInstanceOf(LicenseException.class);
    }

    @Test
    void verify_shouldTreatNonBooleanValidAsFailOpenNotValid() {
        // valid 字段存在但非布尔（解析异常场景）：同样只能算放行，不能算明确有效
        responseBody = "{\"data\":{\"valid\":\"yes\",\"reason\":\"x\"}}";
        HttpRemoteVerifyProvider p = provider(3600, 0, 5, 300);
        assertThatCode(() -> p.verify(content(), "f1")).doesNotThrowAnyException();

        responseBody = "{\"data\":{\"valid\":false,\"reason\":\"revoked\"}}";
        assertThatThrownBy(() -> p.verify(content(), "f1")).isInstanceOf(LicenseException.class);
    }

    @Test
    void verify_shouldSkipSecondCallWithinCacheWindow() {
        HttpRemoteVerifyProvider p = provider();
        p.verify(content(), "f1");
        assertThat(requestCount).isEqualTo(1);

        // 窗口内（默认 1 小时）第二次校验直接放行，不再发 HTTP
        p.verify(content(), "f1");
        assertThat(requestCount).isEqualTo(1);
    }

    @Test
    void verify_shouldNotCacheRejectedResult() {
        responseBody = "{\"data\":{\"valid\":false,\"reason\":\"授权已被吊销\"}}";
        HttpRemoteVerifyProvider p = provider();
        assertThatThrownBy(() -> p.verify(content(), "f1")).isInstanceOf(LicenseException.class);

        // 明确拒绝不缓存：服务端恢复后应能立即重新校验通过
        responseBody = "{\"data\":{\"valid\":true,\"reason\":\"ok\"}}";
        assertThatCode(() -> p.verify(content(), "f1")).doesNotThrowAnyException();
        assertThat(requestCount).isEqualTo(2);
    }

    @Test
    void verify_shouldCacheFailOpenResultWithinFailOpenWindow() {
        status = 500;
        HttpRemoteVerifyProvider p = provider(3600, 3600, 5, 300);
        p.verify(content(), "f1");
        assertThat(requestCount).isEqualTo(1);

        // 放行窗口内第二次调用直接放行，不重复联机（防打爆）
        p.verify(content(), "f1");
        assertThat(requestCount).isEqualTo(1);
    }

    @Test
    void verify_shouldRetryEveryCallWhenFailOpenWindowDisabled() {
        status = 500;
        HttpRemoteVerifyProvider p = provider(3600, 0, 5, 300);
        p.verify(content(), "f1");
        assertThat(requestCount).isEqualTo(1);

        p.verify(content(), "f1");
        assertThat(requestCount).isEqualTo(2);
    }

    @Test
    void verify_shouldEscalateToBackoffWindowAfterConsecutiveFailures() {
        status = 500;
        // 放行窗口关闭（每次都会重试），阈值 2：连续 2 次放行后升级为长退避窗口
        HttpRemoteVerifyProvider p = provider(3600, 0, 2, 3600);
        p.verify(content(), "f1");
        assertThat(requestCount).isEqualTo(1);

        p.verify(content(), "f1");
        assertThat(requestCount).isEqualTo(2);

        // 已达阈值进入退避窗口，第三次直接放行不再联机
        p.verify(content(), "f1");
        assertThat(requestCount).isEqualTo(2);
    }

    @Test
    void verify_shouldResetFailOpenCountOnExplicitResponse() {
        status = 500;
        HttpRemoteVerifyProvider p = provider(3600, 0, 2, 3600);
        p.verify(content(), "f1");
        assertThat(requestCount).isEqualTo(1);

        // 服务端恢复并明确返回有效：重置连续放行计数
        status = 200;
        p.verify(content(), "f1");
        assertThat(requestCount).isEqualTo(2);

        // 缓存窗口内直接放行，不再联机
        p.verify(content(), "f1");
        assertThat(requestCount).isEqualTo(2);
    }

    @Test
    void verify_shouldSingleFlightConcurrentCallsOnCacheMiss() throws Exception {
        int threads = 8;
        HttpRemoteVerifyProvider p = provider();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    p.verify(content(), "f1");
                    return null;
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }

        // 缓存未命中时的并发调用应单飞：只有一个线程真正发起联机请求
        assertThat(requestCount).isEqualTo(1);
    }
}

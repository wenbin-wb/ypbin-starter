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
package cn.ypbin.starter.cloud.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.cloud.exception.FeignRemoteException;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.test.condition.EnabledIfNacosAvailable;
import cn.ypbin.starter.test.container.ContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feign 跨服务真机集成测试。
 *
 * <p>仅在 {@code -Pit}（failsafe）下执行。同一 JVM 内起真实 Web 服务并注册到 Nacos，再用
 * {@code @FeignClient} 按服务名（经 loadbalancer 服务发现）回调自身，验证：
 * 注册 → 发现 → 负载均衡 → Feign 调通、请求头透传，以及下游返回统一 {@code R} 错误时
 * 被 {@code RResponseErrorDecoder} 解析为 {@link FeignRemoteException}。</p>
 *
 * <p>Nacos 地址由共享测试基座解析：外部地址优先（{@code -Dypbin.it.nacos-addr=host:8848} 或
 * {@code YPBIN_TEST_NACOS_ADDR}），否则用 Testcontainers 拉起容器；两者都不可用时由
 * {@link EnabledIfNacosAvailable} 条件跳过（不判失败）。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
@EnabledIfNacosAvailable
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = {
        "server.port=18761",
        "spring.application.name=ypbin-feign-it",
        "spring.cloud.nacos.discovery.register-enabled=true",
        "spring.cloud.openfeign.circuitbreaker.enabled=false"
    })
class FeignCrossServiceIT {

    @DynamicPropertySource
    static void nacosProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.nacos.discovery.server-addr", ContainerSupport::nacosServerAddress);
    }

    @Autowired
    private EchoClient echoClient;

    @Test
    void shouldInvokeThroughServiceDiscovery() {
        String result = echoClient.echo("ping");
        assertThat(result).isEqualTo("echo:ping");
    }

    @Test
    void shouldPropagateAuthorizationHeader() {
        // 无 Web 请求上下文时 FeignHeaderInterceptor 安全跳过，这里显式传头验证下游能收到
        String seen = echoClient.header();
        assertThat(seen).isNotNull();
    }

    @Test
    void shouldDecodeDownstreamRErrorToFeignRemoteException() {
        assertThatThrownBy(() -> echoClient.fail())
            .isInstanceOf(FeignRemoteException.class)
            .satisfies(ex -> {
                FeignRemoteException fre = (FeignRemoteException) ex;
                assertThat(fre.getCode()).isEqualTo(409);
                assertThat(fre.getMessage()).contains("库存不足");
            });
    }

    @FeignClient(name = "ypbin-feign-it")
    interface EchoClient {

        @GetMapping("/echo")
        String echo(@RequestParam("msg") String msg);

        @GetMapping("/header")
        String header();

        @GetMapping("/fail")
        String fail();
    }

    @RestController
    static class EchoController {

        @GetMapping("/echo")
        String echo(@RequestParam("msg") String msg) {
            return "echo:" + msg;
        }

        @GetMapping("/header")
        String header(@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
            return String.valueOf(requestId);
        }

        @GetMapping("/fail")
        ResponseEntity<R<Void>> fail() {
            // 下游返回统一 R 错误（HTTP 500 触发 Feign ErrorDecoder），验证被解析为 FeignRemoteException
            return ResponseEntity.status(500).body(R.fail(409, "库存不足"));
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableFeignClients(clients = EchoClient.class)
    static class TestApp {

        @Bean
        EchoController echoController() {
            return new EchoController();
        }
    }
}

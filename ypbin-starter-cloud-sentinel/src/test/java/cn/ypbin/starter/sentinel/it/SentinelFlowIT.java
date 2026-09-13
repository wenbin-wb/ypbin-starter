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
package cn.ypbin.starter.sentinel.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Sentinel 限流真运行时集成测试。
 *
 * <p>仅在 {@code -Pit}（failsafe）下执行，本机即可真跑（不依赖 Nacos/Docker）。真启动 Web 服务 +
 * Sentinel Web 拦截链路，编程式下发一条 QPS=1 的流控规则，连续打同一接口，验证超限请求被
 * {@code RBlockExceptionHandler} 拦截并返回项目统一 {@code R}（code=429），而非 Sentinel 默认纯文本。</p>
 *
 * <p>HTTP 客户端用 {@link RestClient} 而非已随 Spring Boot 4 移除的 {@code TestRestTemplate}；
 * 并关闭默认的状态码错误映射，以便无论被限流时返回 200 还是 429 都能拿到响应体做断言。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SentinelFlowIT {

    /** Sentinel Web 过滤器默认以 URL 路径作为资源名 */
    private static final String RESOURCE = "/limited";

    @LocalServerPort
    private int port;

    private RestClient restClient;

    @BeforeEach
    void loadFlowRule() {
        FlowRule rule = new FlowRule();
        rule.setResource(RESOURCE);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(1);
        FlowRuleManager.loadRules(List.of(rule));
        restClient = RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .defaultStatusHandler(status -> false, (request, response) -> {
            })
            .build();
    }

    @AfterEach
    void clearRules() {
        FlowRuleManager.loadRules(List.of());
    }

    @Test
    void shouldReturnUnifiedRWhenFlowLimited() {
        // 首次放行，第二次同一 tick 内超过 QPS=1 被限流
        String first = restClient.get().uri(RESOURCE).retrieve().body(String.class);
        String second = restClient.get().uri(RESOURCE).retrieve().body(String.class);

        // 至少有一次命中限流并返回统一 R（code=429）
        String limitedBody = second != null && second.contains("429") ? second : first;
        assertThat(limitedBody).isNotNull();
        assertThat(limitedBody).contains("\"code\":429");
        assertThat(limitedBody).contains("\"success\":false");
    }

    @RestController
    static class LimitedController {

        @GetMapping("/limited")
        String limited() {
            return "ok";
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {

        @Bean
        LimitedController limitedController() {
            return new LimitedController();
        }
    }
}

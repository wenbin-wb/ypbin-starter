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
package cn.ypbin.starter.cloud.autoconfigure;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Feign 默认属性注入器。
 *
 * <p>以最低优先级开启 OpenFeign circuitbreaker，使 cloud-core 引入的 Resilience4j 真正参与 Feign
 * 调用链；同时注入一组 resilience4j 的 <em>默认</em> 熔断/超时参数，避免业务未配置时落入库默认的
 * 1s TimeLimiter 硬超时与过严/过松的熔断阈值（如慢接口被误判超时、窗口与判定次数不适合真实流量）。</p>
 *
 * <p>注入值全部挂到 {@code configs.default} 且置于最低优先级属性源：业务方在 application.yml 中
 * 显式声明的同名项优先级更高，可整体或逐项覆盖（未声明项自动沿用本注入的默认）。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class FeignDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "ypbinFeignDefaults";

    /** 熔断窗口大小：按最近 20 次调用统计失败率 */
    private static final String DEFAULT_SLIDING_WINDOW_SIZE = "20";

    /** 熔断判定失败率阈值（%）：窗口内失败率超过 50% 熔断打开 */
    private static final String DEFAULT_FAILURE_RATE_THRESHOLD = "50";

    /** 最小调用次数：窗口内不足该次数不参与熔断判定，避免冷启动误熔断 */
    private static final String DEFAULT_MINIMUM_NUMBER_OF_CALLS = "10";

    /** 半开态最大放行探测调用数 */
    private static final String DEFAULT_PERMITTED_HALF_OPEN_CALLS = "10";

    /** 熔断打开后维持时长，到期自动转半开放行探测 */
    private static final String DEFAULT_WAIT_DURATION_IN_OPEN_STATE = "10s";

    /** TimeLimiter 默认超时（resilience4j 库内建默认仅 1s，业务未配置时慢接口会被误杀） */
    private static final String DEFAULT_TIMELIMITER_TIMEOUT = "10s";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        Boolean enabled = environment.getProperty("ypbin.cloud.feign.circuitbreaker-enabled", Boolean.class, true);
        if (!enabled) {
            return;
        }
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("spring.cloud.openfeign.circuitbreaker.enabled", "true");
        // Spring Cloud 2025.1.2 起官方支持 Spring Boot 4.1.x（官网兼容表），
        // 但内置 CompatibilityVerifier 元数据滞后仍报 4.0.x-only，属误报，禁用该检查
        defaults.put("spring.cloud.compatibility-verifier.enabled", "false");
        // —— resilience4j 默认熔断/超时参数（最低优先级，业务显式配置可整体/逐项覆盖）——
        // TimeLimiter：默认 10s 超时，替换库内建 1s 硬超时
        defaults.put("resilience4j.timelimiter.configs.default.timeout-duration",
            DEFAULT_TIMELIMITER_TIMEOUT);
        // CircuitBreaker 计数滑动窗口：20 次窗口 / 50% 失败率 / 至少 10 次才判定 / 半开放行 10 次 / 打开 10s
        defaults.put("resilience4j.circuitbreaker.configs.default.sliding-window-size",
            DEFAULT_SLIDING_WINDOW_SIZE);
        defaults.put("resilience4j.circuitbreaker.configs.default.failure-rate-threshold",
            DEFAULT_FAILURE_RATE_THRESHOLD);
        defaults.put("resilience4j.circuitbreaker.configs.default.minimum-number-of-calls",
            DEFAULT_MINIMUM_NUMBER_OF_CALLS);
        defaults.put(
            "resilience4j.circuitbreaker.configs.default.permitted-number-of-calls-in-half-open-state",
            DEFAULT_PERMITTED_HALF_OPEN_CALLS);
        defaults.put("resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state",
            DEFAULT_WAIT_DURATION_IN_OPEN_STATE);
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

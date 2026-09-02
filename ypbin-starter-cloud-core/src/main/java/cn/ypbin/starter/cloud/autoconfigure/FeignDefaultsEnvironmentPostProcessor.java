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
 * 调用链。业务方显式配置优先级更高，可随时覆盖。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class FeignDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "ypbinFeignDefaults";

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
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

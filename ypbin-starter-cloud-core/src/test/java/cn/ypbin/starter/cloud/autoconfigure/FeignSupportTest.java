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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

/**
 * Feign 配置与默认环境后置处理测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class FeignSupportTest {

    @Test
    void propertiesShouldExposeDefaults() {
        FeignProperties props = new FeignProperties();
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.isErrorDecoderEnabled()).isTrue();
        assertThat(props.isCircuitbreakerEnabled()).isTrue();
        assertThat(props.getPropagateHeaders()).contains("Authorization");
        // 身份头默认透传（二次 RPC 保下游识别调用者身份）
        assertThat(props.getPropagateHeaders())
            .contains("X-User-Id", "X-User-Name", "X-Tenant-Id", "X-Dept-Id", "X-Roles");
    }

    @Test
    void defaultsShouldInjectProperties() {
        FeignDefaultsEnvironmentPostProcessor processor = new FeignDefaultsEnvironmentPostProcessor();
        StandardEnvironment env = new StandardEnvironment();
        processor.postProcessEnvironment(env, null);
        assertThat(env.getProperty("spring.cloud.openfeign.circuitbreaker.enabled")).isNotNull();
        // Spring Cloud 2025.1.2+ 官方支持 Boot 4.1.x，禁用滞后的兼容性检查（误报）
        assertThat(env.getProperty("spring.cloud.compatibility-verifier.enabled")).isEqualTo("false");
    }

    @Test
    void defaultsShouldSkipWhenDisabled() {
        FeignDefaultsEnvironmentPostProcessor processor = new FeignDefaultsEnvironmentPostProcessor();
        MockEnvironment env = new MockEnvironment();
        env.setProperty("ypbin.cloud.feign.circuitbreaker-enabled", "false");
        processor.postProcessEnvironment(env, null);
        assertThat(env.getProperty("spring.cloud.openfeign.circuitbreaker.enabled")).isNull();
    }
}

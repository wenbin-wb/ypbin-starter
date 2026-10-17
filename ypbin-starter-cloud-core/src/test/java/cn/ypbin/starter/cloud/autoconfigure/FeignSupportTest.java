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

    /**
     * 内部凭证配置的默认值与「默认不泄露」约定。
     *
     * <p>本用例同时是一道<b>回归护栏</b>：{@code FeignProperties} 目前不覆写 {@code toString}
     * （默认实现只输出类名与哈希，天然不含字段值）。若将来给它加 {@code @Data}/{@code @ToString}
     * 之类的全字段输出，凭证就会在任何「打印配置对象」的日志里泄露——本断言会立刻失败。</p>
     */
    @Test
    void internalTokenDefaultsShouldMatchGuardHeaderAndNotLeak() {
        FeignProperties props = new FeignProperties();
        assertThat(props.getInternalTokenHeader())
            .as("默认头名必须与 security 守卫侧 InternalTokenProperties.DEFAULT_HEADER_NAME 逐字一致")
            .isEqualTo("X-Internal-Token");
        assertThat(props.getInternalToken()).isEmpty();

        props.setInternalToken("real-secret-value");
        assertThat(props.toString()).doesNotContain("real-secret-value");
    }

    @Test
    void defaultsShouldInjectProperties() {
        FeignDefaultsEnvironmentPostProcessor processor = new FeignDefaultsEnvironmentPostProcessor();
        StandardEnvironment env = new StandardEnvironment();
        processor.postProcessEnvironment(env, null);
        assertThat(env.getProperty("spring.cloud.openfeign.circuitbreaker.enabled")).isNotNull();
        // Spring Cloud 2025.1.2+ 官方支持 Boot 4.1.x，禁用滞后的兼容性检查（误报）
        assertThat(env.getProperty("spring.cloud.compatibility-verifier.enabled")).isEqualTo("false");
        // 连接/读取超时必须显式注入，杜绝无超时默认客户端
        assertThat(env.getProperty("spring.cloud.openfeign.client.config.default.connect-timeout"))
            .isEqualTo("5000");
        assertThat(env.getProperty("spring.cloud.openfeign.client.config.default.read-timeout"))
            .isEqualTo("10000");
    }

    @Test
    void timeoutsShouldStillApplyWhenCircuitBreakerDisabled() {
        FeignDefaultsEnvironmentPostProcessor processor = new FeignDefaultsEnvironmentPostProcessor();
        MockEnvironment env = new MockEnvironment();
        env.setProperty("ypbin.cloud.feign.circuitbreaker-enabled", "false");
        processor.postProcessEnvironment(env, null);
        // 熔断可关，但超时是基础安全项，必须仍然注入
        assertThat(env.getProperty("spring.cloud.openfeign.client.config.default.connect-timeout"))
            .isEqualTo("5000");
        assertThat(env.getProperty("spring.cloud.openfeign.client.config.default.read-timeout"))
            .isEqualTo("10000");
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

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
package cn.ypbin.starter.messaging.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * {@link SmsDefaultsEnvironmentPostProcessor} 单元测试：默认 banner 属性注入且优先级最低、可被用户覆盖。
 *
 * @author wenbin
 * @since 2026-08-06
 */
class SmsDefaultsEnvironmentPostProcessorTest {

    private final SmsDefaultsEnvironmentPostProcessor processor = new SmsDefaultsEnvironmentPostProcessor();

    @Test
    void shouldInjectSmsPrintDefault() {
        StandardEnvironment environment = new StandardEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("sms.is-print")).isEqualTo("false");
    }

    @Test
    void shouldNotOverrideUserConfiguredPrint() {
        StandardEnvironment environment = new StandardEnvironment();
        // 用户显式配置优先级更高，后处理器的默认值不得覆盖
        environment.getPropertySources().addFirst(new MapPropertySource("user", Map.of(
            "sms.is-print", "true")));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("sms.is-print")).isEqualTo("true");
    }
}

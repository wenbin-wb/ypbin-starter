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
package cn.ypbin.starter.cloud.launch.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * {@link CloudLaunchEnvironmentPostProcessor} 单元测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class CloudLaunchEnvironmentPostProcessorTest {

    private final CloudLaunchEnvironmentPostProcessor processor = new CloudLaunchEnvironmentPostProcessor();

    @Test
    void shouldAddNacosImportDefaults() {
        StandardEnvironment environment = new StandardEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.config.import")).isEqualTo("optional:nacos:application.yml");
        assertThat(environment.getProperty("spring.cloud.nacos.config.import-check.enabled")).isEqualTo("false");
    }

    @Test
    void shouldRespectUserConfigImport() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", java.util.Map.of(
            "spring.config.import", "optional:nacos:custom.yml",
            "spring.cloud.nacos.config.import-check.enabled", "true"
        )));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.config.import")).isEqualTo("optional:nacos:custom.yml");
        assertThat(environment.getProperty("spring.cloud.nacos.config.import-check.enabled")).isEqualTo("true");
    }

    @Test
    void shouldBackOffWhenDisabled() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", java.util.Map.of(
            "ypbin.cloud.launch.enabled", "false"
        )));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.config.import")).isNull();
        assertThat(environment.getProperty("spring.cloud.nacos.config.import-check.enabled")).isNull();
    }
}

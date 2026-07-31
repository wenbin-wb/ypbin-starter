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
package cn.ypbin.starter.nacos.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * {@link NacosEnvironmentPostProcessor} 单元测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class NacosEnvironmentPostProcessorTest {

    private final NacosEnvironmentPostProcessor processor = new NacosEnvironmentPostProcessor();

    @Test
    void shouldAddNacosImportDefaults() {
        StandardEnvironment environment = new StandardEnvironment();

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.profiles.default")).isEqualTo("dev");
        assertThat(environment.getProperty("spring.config.import"))
            .isEqualTo("optional:nacos:application.yaml,optional:nacos:application-dev.yaml");
        assertThat(environment.getProperty("spring.cloud.nacos.config.import-check.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("nacos.logging.default.config.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("management.info.process.enabled")).isEqualTo("true");
        assertThat(environment.getProperty("spring.main.allow-bean-definition-overriding")).isEqualTo("false");
    }

    @Test
    void shouldAddApplicationInfoWhenConfigured() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", java.util.Map.of(
            "ypbin.cloud.nacos.application-name", "order-service",
            "ypbin.cloud.nacos.application-description", "Order Service",
            "ypbin.cloud.nacos.service-version", "1.2.3"
        )));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.application.name")).isEqualTo("order-service");
        assertThat(environment.getProperty("info.desc")).isEqualTo("Order Service");
        assertThat(environment.getProperty("info.version")).isEqualTo("1.2.3");
    }

    @Test
    void shouldAddApplicationProfileImportWhenApplicationNameExists() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("test");
        environment.getPropertySources().addFirst(new MapPropertySource("test", java.util.Map.of(
            "spring.application.name", "order-service",
            "ypbin.cloud.nacos.config-prefix", "ypbin"
        )));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.config.import"))
            .isEqualTo("optional:nacos:ypbin.yaml,optional:nacos:ypbin-test.yaml,optional:nacos:order-service-test.yaml");
    }

    @Test
    void shouldUseConfiguredApplicationNameForNacosImport() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("prod");
        environment.getPropertySources().addFirst(new MapPropertySource("test", java.util.Map.of(
            "ypbin.cloud.nacos.application-name", "order-service"
        )));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.application.name")).isEqualTo("order-service");
        assertThat(environment.getProperty("spring.config.import"))
            .isEqualTo("optional:nacos:application.yaml,optional:nacos:application-prod.yaml,optional:nacos:order-service-prod.yaml");
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
    void shouldRespectExplicitNacosConfigImport() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", java.util.Map.of(
            "ypbin.cloud.nacos.config-import", "optional:nacos:base.yml,optional:nacos:biz.yml"
        )));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.config.import"))
            .isEqualTo("optional:nacos:base.yml,optional:nacos:biz.yml");
    }

    @Test
    void shouldBackOffWhenDisabled() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", java.util.Map.of(
            "ypbin.cloud.nacos.enabled", "false"
        )));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("spring.config.import")).isNull();
        assertThat(environment.getProperty("spring.cloud.nacos.config.import-check.enabled")).isNull();
    }

    @Test
    void shouldRejectMultiplePresetProfiles() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("dev", "prod");

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("只能同时激活 dev/test/prod 中的一个环境");
    }
}

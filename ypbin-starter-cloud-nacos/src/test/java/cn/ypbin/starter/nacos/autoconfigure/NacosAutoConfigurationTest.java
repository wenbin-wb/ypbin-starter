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

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * {@link NacosAutoConfiguration} 自动配置装配测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class NacosAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(NacosAutoConfiguration.class));

    @Test
    void shouldBindPropertiesByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(NacosProperties.class);
            assertThat(context.getBean(NacosProperties.class).isEnabled()).isTrue();
        });
    }

    @Test
    void shouldBackOffWhenDisabled() {
        runner.withPropertyValues("ypbin.cloud.nacos.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(NacosProperties.class));
    }
}

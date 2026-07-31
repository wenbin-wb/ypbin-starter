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
package cn.ypbin.starter.loadbalancer.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import cn.ypbin.starter.loadbalancer.core.VersionRequestContextResolver;
import cn.ypbin.starter.loadbalancer.core.VersionServiceInstanceChooser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;

/**
 * {@link LoadBalancerAutoConfiguration} 自动配置装配测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class LoadBalancerAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withBean(LoadBalancerClientFactory.class, () -> mock(LoadBalancerClientFactory.class))
        .withConfiguration(AutoConfigurations.of(LoadBalancerAutoConfiguration.class));

    @Test
    void shouldRegisterCoreBeansByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(VersionRequestContextResolver.class);
            assertThat(context).hasSingleBean(VersionServiceInstanceChooser.class);
            assertThat(context).hasSingleBean(LoadBalancerProperties.class);
        });
    }

    @Test
    void shouldBackOffWhenDisabled() {
        runner.withPropertyValues("ypbin.cloud.loadbalancer.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(VersionServiceInstanceChooser.class);
                assertThat(context).doesNotHaveBean(VersionRequestContextResolver.class);
            });
    }

    @Test
    void shouldBindProperties() {
        runner.withPropertyValues(
                "ypbin.cloud.loadbalancer.version=gray",
                "ypbin.cloud.loadbalancer.metadata-key=app-version",
                "ypbin.cloud.loadbalancer.fallback-to-stable=false")
            .run(context -> {
                LoadBalancerProperties properties = context.getBean(LoadBalancerProperties.class);
                assertThat(properties.getVersion()).isEqualTo("gray");
                assertThat(properties.getMetadataKey()).isEqualTo("app-version");
                assertThat(properties.isFallbackToStable()).isFalse();
            });
    }
}

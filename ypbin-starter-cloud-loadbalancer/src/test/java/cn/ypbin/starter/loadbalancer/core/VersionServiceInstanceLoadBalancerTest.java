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
package cn.ypbin.starter.loadbalancer.core;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.loadbalancer.autoconfigure.LoadBalancerEnvironmentPostProcessor;
import cn.ypbin.starter.loadbalancer.autoconfigure.LoadBalancerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockEnvironment;

/**
 * 版本负载均衡配置与后置处理测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class VersionServiceInstanceLoadBalancerTest {

    @Test
    void propertiesShouldExposeDefaults() {
        LoadBalancerProperties props = new LoadBalancerProperties();
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getMetadataKey()).isEqualTo("version");
        assertThat(props.getDefaultWeight()).isEqualTo(1);
        assertThat(props.getVersionHeaders()).contains("X-Version");
    }

    @Test
    void defaultsShouldInjectProperties() {
        LoadBalancerEnvironmentPostProcessor processor = new LoadBalancerEnvironmentPostProcessor();
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(
            new org.springframework.core.env.MapPropertySource("test",
                java.util.Map.of("ypbin.cloud.loadbalancer.version", "1.0.0")));
        processor.postProcessEnvironment(env, null);
        assertThat(env.getProperty("spring.cloud.nacos.discovery.metadata.version")).isEqualTo("1.0.0");
    }

    @Test
    void chooseShouldReturnEmptyWhenNoInstances() {
        LoadBalancerProperties props = new LoadBalancerProperties();
        VersionServiceInstanceChooser chooser = new VersionServiceInstanceChooser(props);
        VersionRequestContextResolver resolver = new VersionRequestContextResolver(props);
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier> provider =
            org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable(org.mockito.ArgumentMatchers.any()))
            .thenReturn(new org.springframework.cloud.loadbalancer.core.NoopServiceInstanceListSupplier());
        cn.ypbin.starter.loadbalancer.core.VersionServiceInstanceLoadBalancer lb =
            new cn.ypbin.starter.loadbalancer.core.VersionServiceInstanceLoadBalancer(
                provider, resolver, chooser);
        org.springframework.cloud.client.loadbalancer.Request request =
            org.mockito.Mockito.mock(org.springframework.cloud.client.loadbalancer.Request.class);
        assertThat(lb.choose(request).block()).isNotNull();
    }

    @Test
    void defaultsShouldSkipWhenDisabled() {
        LoadBalancerEnvironmentPostProcessor processor = new LoadBalancerEnvironmentPostProcessor();
        MockEnvironment env = new MockEnvironment();
        env.setProperty("ypbin.cloud.loadbalancer.register-nacos-metadata", "false");
        processor.postProcessEnvironment(env, null);
        assertThat(env.getProperty("spring.cloud.nacos.discovery.metadata.version")).isNull();
    }
}

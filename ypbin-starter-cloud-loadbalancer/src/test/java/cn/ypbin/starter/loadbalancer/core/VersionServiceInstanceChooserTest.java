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

import cn.ypbin.starter.loadbalancer.autoconfigure.LoadBalancerProperties;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.ServiceInstance;

/**
 * {@link VersionServiceInstanceChooser} 单元测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class VersionServiceInstanceChooserTest {

    @Test
    void shouldChooseMatchingVersionInstance() {
        LoadBalancerProperties properties = new LoadBalancerProperties();
        VersionServiceInstanceChooser chooser = new VersionServiceInstanceChooser(properties);
        ServiceInstance stable = instance("stable", "10.0.0.1", Map.of());
        ServiceInstance gray = instance("gray", "10.0.0.2", Map.of("version", "gray"));

        ServiceInstance chosen = chooser.choose(List.of(stable, gray), "gray", 0);

        assertThat(chosen.getInstanceId()).isEqualTo("gray");
    }

    @Test
    void shouldPreferStableInstancesWhenRequestHasNoVersion() {
        LoadBalancerProperties properties = new LoadBalancerProperties();
        VersionServiceInstanceChooser chooser = new VersionServiceInstanceChooser(properties);
        ServiceInstance stable = instance("stable", "10.0.0.1", Map.of());
        ServiceInstance gray = instance("gray", "10.0.0.2", Map.of("version", "gray"));

        ServiceInstance chosen = chooser.choose(List.of(stable, gray), null, 0);

        assertThat(chosen.getInstanceId()).isEqualTo("stable");
    }

    @Test
    void shouldFallbackToStableWhenVersionNotMatched() {
        LoadBalancerProperties properties = new LoadBalancerProperties();
        properties.setFallbackToStable(true);
        VersionServiceInstanceChooser chooser = new VersionServiceInstanceChooser(properties);
        ServiceInstance stable = instance("stable", "10.0.0.1", Map.of());
        ServiceInstance gray = instance("gray", "10.0.0.2", Map.of("version", "gray"));

        ServiceInstance chosen = chooser.choose(List.of(stable, gray), "beta", 0);

        assertThat(chosen.getInstanceId()).isEqualTo("stable");
    }

    @Test
    void shouldReturnNullWhenVersionNotMatchedAndFallbackDisabled() {
        LoadBalancerProperties properties = new LoadBalancerProperties();
        properties.setFallbackToStable(false);
        VersionServiceInstanceChooser chooser = new VersionServiceInstanceChooser(properties);
        ServiceInstance stable = instance("stable", "10.0.0.1", Map.of());
        ServiceInstance gray = instance("gray", "10.0.0.2", Map.of("version", "gray"));

        ServiceInstance chosen = chooser.choose(List.of(stable, gray), "beta", 0);

        assertThat(chosen).isNull();
    }

    @Test
    void shouldApplyPriorIpPatternsBeforeVersionSelection() {
        LoadBalancerProperties properties = new LoadBalancerProperties();
        properties.setPriorIpPatterns(List.of("10.1.0.*"));
        VersionServiceInstanceChooser chooser = new VersionServiceInstanceChooser(properties);
        ServiceInstance stable = instance("stable", "10.0.0.1", Map.of());
        ServiceInstance priorGray = instance("priorGray", "10.1.0.8", Map.of("version", "gray"));
        ServiceInstance normalGray = instance("normalGray", "10.0.0.9", Map.of("version", "gray"));

        ServiceInstance chosen = chooser.choose(List.of(stable, priorGray, normalGray), "gray", 0);

        assertThat(chosen.getInstanceId()).isEqualTo("priorGray");
    }

    @Test
    void shouldChooseByMetadataWeight() {
        LoadBalancerProperties properties = new LoadBalancerProperties();
        VersionServiceInstanceChooser chooser = new VersionServiceInstanceChooser(properties);
        ServiceInstance first = instance("first", "10.0.0.1", Map.of("weight", "1"));
        ServiceInstance second = instance("second", "10.0.0.2", Map.of("weight", "3"));

        ServiceInstance firstChosen = chooser.choose(List.of(first, second), null, 0);
        ServiceInstance secondChosen = chooser.choose(List.of(first, second), null, 1);

        assertThat(firstChosen.getInstanceId()).isEqualTo("first");
        assertThat(secondChosen.getInstanceId()).isEqualTo("second");
    }

    @Test
    void shouldUseCustomMetadataKey() {
        LoadBalancerProperties properties = new LoadBalancerProperties();
        properties.setMetadataKey("app-version");
        VersionServiceInstanceChooser chooser = new VersionServiceInstanceChooser(properties);
        ServiceInstance stable = instance("stable", "10.0.0.1", Map.of());
        ServiceInstance gray = instance("gray", "10.0.0.2", Map.of("app-version", "v2"));

        ServiceInstance chosen = chooser.choose(List.of(stable, gray), "v2", 0);

        assertThat(chosen.getInstanceId()).isEqualTo("gray");
    }

    private static ServiceInstance instance(String id, String host, Map<String, String> metadata) {
        return new TestServiceInstance(id, host, metadata);
    }

    private record TestServiceInstance(String instanceId, String host, Map<String, String> metadata)
        implements ServiceInstance {

        private TestServiceInstance {
            metadata = new HashMap<>(metadata);
        }

        @Override
        public String getServiceId() {
            return "demo-service";
        }

        @Override
        public String getHost() {
            return host;
        }

        @Override
        public int getPort() {
            return 8080;
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public URI getUri() {
            return URI.create("http://" + host + ":" + getPort());
        }

        @Override
        public Map<String, String> getMetadata() {
            return metadata;
        }

        @Override
        public String getInstanceId() {
            return instanceId;
        }
    }
}

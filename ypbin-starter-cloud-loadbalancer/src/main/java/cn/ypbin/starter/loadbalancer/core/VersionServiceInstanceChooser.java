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

import cn.ypbin.starter.loadbalancer.autoconfigure.LoadBalancerProperties;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.util.PatternMatchUtils;
import org.springframework.util.StringUtils;

/**
 * 版本灰度服务实例选择器。
 *
 * <p>选择流程：优先 IP 过滤 → 按请求版本匹配 metadata → 无版本请求优先正式实例 → 按权重随机。
 * 纯算法类不依赖 Reactor，便于单元测试与后续复用。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class VersionServiceInstanceChooser {

    private final LoadBalancerProperties properties;

    public VersionServiceInstanceChooser(LoadBalancerProperties properties) {
        this.properties = properties;
    }

    public ServiceInstance choose(List<ServiceInstance> instances, String requestVersion) {
        List<ServiceInstance> candidates = candidates(instances, requestVersion);
        if (candidates.isEmpty()) {
            return null;
        }
        long totalWeight = totalWeight(candidates);
        long weightPoint = ThreadLocalRandom.current().nextLong(totalWeight);
        return chooseByWeight(candidates, weightPoint);
    }

    ServiceInstance choose(List<ServiceInstance> instances, String requestVersion, long weightPoint) {
        List<ServiceInstance> candidates = candidates(instances, requestVersion);
        if (candidates.isEmpty()) {
            return null;
        }
        return chooseByWeight(candidates, weightPoint);
    }

    private List<ServiceInstance> candidates(List<ServiceInstance> instances, String requestVersion) {
        if (instances == null || instances.isEmpty()) {
            return List.of();
        }
        List<ServiceInstance> candidates = filterPriorIp(instances);
        if (StringUtils.hasText(requestVersion)) {
            List<ServiceInstance> versionInstances = candidates.stream()
                .filter(instance -> requestVersion.equalsIgnoreCase(versionOf(instance)))
                .toList();
            if (!versionInstances.isEmpty() || !properties.isFallbackToStable()) {
                return versionInstances;
            }
            return stableInstances(candidates);
        }
        if (!properties.isPreferStableWithoutVersion()) {
            return candidates;
        }
        List<ServiceInstance> stableInstances = stableInstances(candidates);
        return stableInstances.isEmpty() ? candidates : stableInstances;
    }

    private List<ServiceInstance> filterPriorIp(List<ServiceInstance> instances) {
        List<String> priorIpPatterns = properties.getPriorIpPatterns();
        if (priorIpPatterns == null || priorIpPatterns.isEmpty()) {
            return instances;
        }
        String[] patterns = priorIpPatterns.toArray(new String[0]);
        List<ServiceInstance> priorInstances = instances.stream()
            .filter(instance -> PatternMatchUtils.simpleMatch(patterns, instance.getHost()))
            .toList();
        return priorInstances.isEmpty() ? instances : priorInstances;
    }

    private List<ServiceInstance> stableInstances(List<ServiceInstance> instances) {
        return instances.stream()
            .filter(instance -> !StringUtils.hasText(versionOf(instance)))
            .toList();
    }

    private ServiceInstance chooseByWeight(List<ServiceInstance> instances, long weightPoint) {
        long cursor = Math.floorMod(weightPoint, totalWeight(instances));
        for (ServiceInstance instance : instances) {
            cursor -= weightOf(instance);
            if (cursor < 0) {
                return instance;
            }
        }
        return instances.get(instances.size() - 1);
    }

    private long totalWeight(List<ServiceInstance> instances) {
        return instances.stream()
            .mapToLong(this::weightOf)
            .sum();
    }

    private long weightOf(ServiceInstance instance) {
        String value = instance.getMetadata().get(properties.getWeightMetadataKey());
        if (!StringUtils.hasText(value)) {
            return positiveDefaultWeight();
        }
        try {
            return Math.max(1L, Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return positiveDefaultWeight();
        }
    }

    private long positiveDefaultWeight() {
        return Math.max(1L, properties.getDefaultWeight());
    }

    private String versionOf(ServiceInstance instance) {
        return instance.getMetadata().entrySet().stream()
            .filter(entry -> Objects.equals(entry.getKey(), properties.getMetadataKey()))
            .map(entry -> entry.getValue() == null ? null : entry.getValue().trim())
            .filter(StringUtils::hasText)
            .findFirst()
            .orElse(null);
    }
}

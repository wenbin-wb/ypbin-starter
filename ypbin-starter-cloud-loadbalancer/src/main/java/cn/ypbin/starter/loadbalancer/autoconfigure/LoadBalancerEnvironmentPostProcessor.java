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

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * 负载均衡默认环境属性注入器。
 *
 * <p>配置当前服务版本后，默认把版本写入 Nacos discovery metadata，便于其它服务按 metadata
 * 进行版本灰度路由。以最低优先级追加，不覆盖业务方显式配置的 Nacos metadata。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class LoadBalancerEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "ypbinLoadBalancerDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        Boolean registerMetadata = environment.getProperty(
            LoadBalancerProperties.PREFIX + ".register-nacos-metadata", Boolean.class, true);
        if (!registerMetadata) {
            return;
        }
        String version = environment.getProperty(LoadBalancerProperties.PREFIX + ".version");
        if (!StringUtils.hasText(version)) {
            return;
        }
        String metadataKey = environment.getProperty(LoadBalancerProperties.PREFIX + ".metadata-key", "version");
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("spring.cloud.nacos.discovery.metadata." + metadataKey, version.trim());
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

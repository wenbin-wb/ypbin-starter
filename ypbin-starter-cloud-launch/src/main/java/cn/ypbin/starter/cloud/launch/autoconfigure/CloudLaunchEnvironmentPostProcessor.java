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

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * 微服务启动默认属性注入器。
 *
 * <p>Spring Cloud Alibaba Nacos Config 在 Boot 3.x 中依赖 ConfigData 导入。业务方只想先启用注册发现、
 * 暂不接配置中心时，容易因为未配置 {@code spring.config.import} 触发启动检查失败。本注入器以最低优先级追加
 * {@code optional:nacos:application.yml} 与 import-check 默认值：既不覆盖业务显式配置，又能让 cloud 工程开箱启动。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class CloudLaunchEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "ypbinCloudLaunchDefaults";

    private static final String SPRING_CONFIG_IMPORT = "spring.config.import";

    private static final String NACOS_IMPORT_CHECK = "spring.cloud.nacos.config.import-check.enabled";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        Boolean enabled = environment.getProperty(CloudLaunchProperties.PREFIX + ".enabled", Boolean.class, true);
        Boolean nacosImportEnabled = environment.getProperty(
            CloudLaunchProperties.PREFIX + ".nacos-config-import-enabled", Boolean.class, true);
        if (!enabled || !nacosImportEnabled) {
            return;
        }

        Map<String, Object> defaults = new HashMap<>();
        String configuredImport = environment.getProperty(SPRING_CONFIG_IMPORT);
        if (!StringUtils.hasText(configuredImport)) {
            String nacosImport = environment.getProperty(
                CloudLaunchProperties.PREFIX + ".nacos-config-import", "optional:nacos:application.yml");
            if (StringUtils.hasText(nacosImport)) {
                defaults.put(SPRING_CONFIG_IMPORT, nacosImport.trim());
            }
        }
        if (!environment.containsProperty(NACOS_IMPORT_CHECK)) {
            Boolean importCheckEnabled = environment.getProperty(
                CloudLaunchProperties.PREFIX + ".nacos-config-import-check-enabled", Boolean.class, false);
            defaults.put(NACOS_IMPORT_CHECK, String.valueOf(importCheckEnabled));
        }
        if (!defaults.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
        }
    }

    @Override
    public int getOrder() {
        // 必须早于 Spring Boot 的 ConfigDataEnvironmentPostProcessor，spring.config.import 默认值才会参与加载。
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}

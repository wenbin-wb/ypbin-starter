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
package cn.ypbin.starter.apidoc.autoconfigure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * API 文档默认环境属性注入器。
 *
 * <p>生产环境默认关闭 SpringDoc 端点，减少攻击面。以最低优先级注入，业务方显式配置
 * 优先级更高，可随时重新开启。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class ApiDocDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "ypbinApiDocDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        Boolean disableInProd = environment.getProperty("ypbin.api-doc.disable-in-prod", Boolean.class, true);
        if (!disableInProd) {
            return;
        }
        List<String> activeProfiles = List.of(environment.getActiveProfiles());
        if (activeProfiles.stream().noneMatch(profile -> "prod".equalsIgnoreCase(profile))) {
            return;
        }
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("springdoc.api-docs.enabled", "false");
        defaults.put("springdoc.swagger-ui.enabled", "false");
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

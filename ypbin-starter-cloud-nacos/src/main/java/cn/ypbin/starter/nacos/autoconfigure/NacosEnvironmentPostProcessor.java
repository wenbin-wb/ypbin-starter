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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Nacos 启动默认属性注入器。
 *
 * <p>采用 Spring Boot 标准 {@link EnvironmentPostProcessor}，在 ConfigData 加载前注入 Nacos 相关默认值。
 * 所有默认值以最低优先级追加，不覆盖命令行、环境变量、application.yml 等显式配置。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class NacosEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "ypbinNacosDefaults";

    private static final List<String> PRESET_PROFILES = List.of("dev", "test", "prod");

    private static final String SPRING_CONFIG_IMPORT = "spring.config.import";

    private static final String SPRING_APPLICATION_NAME = "spring.application.name";

    private static final String SPRING_PROFILES_ACTIVE = "spring.profiles.active";

    private static final String SPRING_PROFILES_DEFAULT = "spring.profiles.default";

    private static final String NACOS_IMPORT_CHECK = "spring.cloud.nacos.config.import-check.enabled";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        if (!getBoolean(environment, "enabled", true)) {
            return;
        }
        validatePresetProfiles(environment);

        Map<String, Object> defaults = new HashMap<>();
        addApplicationInfo(environment, defaults);
        addDefaultProfile(environment, defaults);
        addNacosConfigImport(environment, defaults);
        addIfAbsent(environment, defaults, "nacos.logging.default.config.enabled",
            String.valueOf(getBoolean(environment, "logging-default-config-enabled", false)));
        addIfAbsent(environment, defaults, "management.info.process.enabled",
            String.valueOf(getBoolean(environment, "management-info-process-enabled", true)));
        addIfAbsent(environment, defaults, "spring.main.allow-bean-definition-overriding",
            String.valueOf(getBoolean(environment, "bean-definition-overriding-enabled", false)));

        if (!defaults.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
        }
    }

    private void validatePresetProfiles(ConfigurableEnvironment environment) {
        if (!getBoolean(environment, "fail-on-multiple-preset-profiles", true)) {
            return;
        }
        Set<String> activeProfiles = activeProfiles(environment);
        List<String> matched = activeProfiles.stream()
            .filter(PRESET_PROFILES::contains)
            .toList();
        if (matched.size() > 1) {
            throw new IllegalStateException("只能同时激活 dev/test/prod 中的一个环境，当前为：" + matched);
        }
    }

    private void addApplicationInfo(ConfigurableEnvironment environment, Map<String, Object> defaults) {
        String applicationName = getString(environment, "application-name", null);
        if (StringUtils.hasText(applicationName)) {
            addIfAbsent(environment, defaults, SPRING_APPLICATION_NAME, applicationName.trim());
            addIfAbsent(environment, defaults, "info.desc", applicationName.trim());
        }
        String description = getString(environment, "application-description", null);
        if (StringUtils.hasText(description)) {
            addIfAbsent(environment, defaults, "info.desc", description.trim());
        }
        String serviceVersion = getString(environment, "service-version", null);
        if (StringUtils.hasText(serviceVersion)) {
            addIfAbsent(environment, defaults, "info.version", serviceVersion.trim());
        }
    }

    private void addDefaultProfile(ConfigurableEnvironment environment, Map<String, Object> defaults) {
        if (!getBoolean(environment, "default-profile-enabled", true)) {
            return;
        }
        if (activeProfiles(environment).isEmpty() && !environment.containsProperty(SPRING_PROFILES_DEFAULT)) {
            String defaultProfile = getString(environment, "default-profile", "dev");
            if (StringUtils.hasText(defaultProfile)) {
                defaults.put(SPRING_PROFILES_DEFAULT, defaultProfile.trim());
            }
        }
    }

    private void addNacosConfigImport(ConfigurableEnvironment environment, Map<String, Object> defaults) {
        if (!getBoolean(environment, "config-import-enabled", true)) {
            return;
        }
        if (!StringUtils.hasText(environment.getProperty(SPRING_CONFIG_IMPORT))) {
            String configuredImport = getString(environment, "config-import", null);
            defaults.put(SPRING_CONFIG_IMPORT, StringUtils.hasText(configuredImport)
                ? configuredImport.trim()
                : buildNacosConfigImport(environment));
        }
        addIfAbsent(environment, defaults, NACOS_IMPORT_CHECK,
            String.valueOf(getBoolean(environment, "config-import-check-enabled", false)));
    }

    private String buildNacosConfigImport(ConfigurableEnvironment environment) {
        String prefix = getString(environment, "config-prefix", "application");
        String extension = getString(environment, "config-file-extension", "yaml");
        String appName = environment.getProperty(SPRING_APPLICATION_NAME);
        if (!StringUtils.hasText(appName)) {
            appName = getString(environment, "application-name", null);
        }
        if (!StringUtils.hasText(prefix)) {
            prefix = "application";
        }
        if (!StringUtils.hasText(extension)) {
            extension = "yaml";
        }
        String profile = currentProfile(environment);
        List<String> imports = new ArrayList<>();
        imports.add(dataId(prefix.trim(), extension.trim()));
        if (StringUtils.hasText(profile) && getBoolean(environment, "include-profile-config", true)) {
            imports.add(dataId(prefix.trim() + "-" + profile.trim(), extension.trim()));
        }
        if (StringUtils.hasText(appName) && StringUtils.hasText(profile)
                && getBoolean(environment, "include-application-profile-config", true)) {
            imports.add(dataId(appName.trim() + "-" + profile.trim(), extension.trim()));
        }
        return String.join(",", imports);
    }

    private String currentProfile(ConfigurableEnvironment environment) {
        Set<String> activeProfiles = activeProfiles(environment);
        if (!activeProfiles.isEmpty()) {
            return activeProfiles.iterator().next();
        }
        return getBoolean(environment, "default-profile-enabled", true)
            ? getString(environment, "default-profile", "dev")
            : null;
    }

    private Set<String> activeProfiles(ConfigurableEnvironment environment) {
        Set<String> profiles = new LinkedHashSet<>();
        for (String profile : environment.getActiveProfiles()) {
            if (StringUtils.hasText(profile)) {
                profiles.add(profile.trim());
            }
        }
        String configuredProfiles = environment.getProperty(SPRING_PROFILES_ACTIVE);
        if (StringUtils.hasText(configuredProfiles)) {
            String[] parts = configuredProfiles.split(",");
            for (String part : parts) {
                if (StringUtils.hasText(part)) {
                    profiles.add(part.trim());
                }
            }
        }
        return profiles;
    }

    private String dataId(String name, String extension) {
        return "optional:nacos:" + name + "." + extension;
    }

    private void addIfAbsent(ConfigurableEnvironment environment, Map<String, Object> defaults, String key, String value) {
        if (!environment.containsProperty(key)) {
            defaults.put(key, value);
        }
    }

    private Boolean getBoolean(ConfigurableEnvironment environment, String key, boolean defaultValue) {
        return environment.getProperty(NacosProperties.PREFIX + "." + key, Boolean.class, defaultValue);
    }

    private String getString(ConfigurableEnvironment environment, String key, String defaultValue) {
        return environment.getProperty(NacosProperties.PREFIX + "." + key, defaultValue);
    }

    @Override
    public int getOrder() {
        // 必须早于 Spring Boot 的 ConfigDataEnvironmentPostProcessor，spring.config.import 默认值才会参与加载。
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }
}

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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nacos 启动增强配置项。
 *
 * @author wenbin
 * @since 2026-07-31
 */
@ConfigurationProperties(prefix = NacosProperties.PREFIX)
public class NacosProperties {

    public static final String PREFIX = "ypbin.cloud.nacos";

    /** 是否启用 Nacos 启动增强，默认开启 */
    private boolean enabled = true;

    /** 无 active profile 时是否注入默认 profile */
    private boolean defaultProfileEnabled = true;

    /** 默认 profile；仅在无 active profile 时以低优先级写入 spring.profiles.default */
    private String defaultProfile = "dev";

    /** 是否禁止 dev/test/prod 同时激活，默认禁止 */
    private boolean failOnMultiplePresetProfiles = true;

    /** 应用名兜底值；为空时不注入 spring.application.name */
    private String applicationName;

    /** 应用描述兜底值；为空时不注入 info.desc */
    private String applicationDescription;

    /** 服务版本兜底值；为空时不注入 info.version */
    private String serviceVersion;

    /** 是否注入 Nacos ConfigData 导入默认值，默认开启 */
    private boolean configImportEnabled = true;

    /** Nacos ConfigData 导入地址；为空时按 prefix/profile/applicationName 自动生成 */
    private String configImport;

    /** Nacos 公共配置前缀 */
    private String configPrefix = "application";

    /** Nacos 配置文件后缀 */
    private String configFileExtension = "yaml";

    /** 是否加载 profile 级配置，如 application-dev.yaml */
    private boolean includeProfileConfig = true;

    /** 是否加载应用 profile 级配置，如 order-service-dev.yaml */
    private boolean includeApplicationProfileConfig = true;

    /** Nacos config import 检查开关；默认关闭检查，避免仅引入配置中心但未显式配置 import 时启动失败 */
    private boolean configImportCheckEnabled = false;

    /** 是否启用 Nacos 默认日志配置；默认关闭，避免 Nacos 覆盖应用日志体系 */
    private boolean loggingDefaultConfigEnabled = false;

    /** 是否开启 Actuator process info */
    private boolean managementInfoProcessEnabled = true;

    /** 是否允许 Bean 覆盖；默认不开启，避免掩盖重复 Bean 问题 */
    private boolean beanDefinitionOverridingEnabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDefaultProfileEnabled() {
        return defaultProfileEnabled;
    }

    public void setDefaultProfileEnabled(boolean defaultProfileEnabled) {
        this.defaultProfileEnabled = defaultProfileEnabled;
    }

    public String getDefaultProfile() {
        return defaultProfile;
    }

    public void setDefaultProfile(String defaultProfile) {
        this.defaultProfile = defaultProfile;
    }

    public boolean isFailOnMultiplePresetProfiles() {
        return failOnMultiplePresetProfiles;
    }

    public void setFailOnMultiplePresetProfiles(boolean failOnMultiplePresetProfiles) {
        this.failOnMultiplePresetProfiles = failOnMultiplePresetProfiles;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getApplicationDescription() {
        return applicationDescription;
    }

    public void setApplicationDescription(String applicationDescription) {
        this.applicationDescription = applicationDescription;
    }

    public String getServiceVersion() {
        return serviceVersion;
    }

    public void setServiceVersion(String serviceVersion) {
        this.serviceVersion = serviceVersion;
    }

    public boolean isConfigImportEnabled() {
        return configImportEnabled;
    }

    public void setConfigImportEnabled(boolean configImportEnabled) {
        this.configImportEnabled = configImportEnabled;
    }

    public String getConfigImport() {
        return configImport;
    }

    public void setConfigImport(String configImport) {
        this.configImport = configImport;
    }

    public String getConfigPrefix() {
        return configPrefix;
    }

    public void setConfigPrefix(String configPrefix) {
        this.configPrefix = configPrefix;
    }

    public String getConfigFileExtension() {
        return configFileExtension;
    }

    public void setConfigFileExtension(String configFileExtension) {
        this.configFileExtension = configFileExtension;
    }

    public boolean isIncludeProfileConfig() {
        return includeProfileConfig;
    }

    public void setIncludeProfileConfig(boolean includeProfileConfig) {
        this.includeProfileConfig = includeProfileConfig;
    }

    public boolean isIncludeApplicationProfileConfig() {
        return includeApplicationProfileConfig;
    }

    public void setIncludeApplicationProfileConfig(boolean includeApplicationProfileConfig) {
        this.includeApplicationProfileConfig = includeApplicationProfileConfig;
    }

    public boolean isConfigImportCheckEnabled() {
        return configImportCheckEnabled;
    }

    public void setConfigImportCheckEnabled(boolean configImportCheckEnabled) {
        this.configImportCheckEnabled = configImportCheckEnabled;
    }

    public boolean isLoggingDefaultConfigEnabled() {
        return loggingDefaultConfigEnabled;
    }

    public void setLoggingDefaultConfigEnabled(boolean loggingDefaultConfigEnabled) {
        this.loggingDefaultConfigEnabled = loggingDefaultConfigEnabled;
    }

    public boolean isManagementInfoProcessEnabled() {
        return managementInfoProcessEnabled;
    }

    public void setManagementInfoProcessEnabled(boolean managementInfoProcessEnabled) {
        this.managementInfoProcessEnabled = managementInfoProcessEnabled;
    }

    public boolean isBeanDefinitionOverridingEnabled() {
        return beanDefinitionOverridingEnabled;
    }

    public void setBeanDefinitionOverridingEnabled(boolean beanDefinitionOverridingEnabled) {
        this.beanDefinitionOverridingEnabled = beanDefinitionOverridingEnabled;
    }
}

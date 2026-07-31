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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 微服务启动增强配置项。
 *
 * @author wenbin
 * @since 2026-07-31
 */
@ConfigurationProperties(prefix = CloudLaunchProperties.PREFIX)
public class CloudLaunchProperties {

    public static final String PREFIX = "ypbin.cloud.launch";

    /** 是否启用启动增强，默认开启 */
    private boolean enabled = true;

    /** 是否注入 Nacos ConfigData 导入默认值，默认开启 */
    private boolean nacosConfigImportEnabled = true;

    /** Nacos ConfigData 导入地址；默认 optional:nacos:application.yml，可被业务配置覆盖 */
    private String nacosConfigImport = "optional:nacos:application.yml";

    /** 是否关闭 Nacos config import 检查；默认关闭检查，避免仅引入配置中心但未显式配置 import 时启动失败 */
    private boolean nacosConfigImportCheckEnabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isNacosConfigImportEnabled() {
        return nacosConfigImportEnabled;
    }

    public void setNacosConfigImportEnabled(boolean nacosConfigImportEnabled) {
        this.nacosConfigImportEnabled = nacosConfigImportEnabled;
    }

    public String getNacosConfigImport() {
        return nacosConfigImport;
    }

    public void setNacosConfigImport(String nacosConfigImport) {
        this.nacosConfigImport = nacosConfigImport;
    }

    public boolean isNacosConfigImportCheckEnabled() {
        return nacosConfigImportCheckEnabled;
    }

    public void setNacosConfigImportCheckEnabled(boolean nacosConfigImportCheckEnabled) {
        this.nacosConfigImportCheckEnabled = nacosConfigImportCheckEnabled;
    }
}

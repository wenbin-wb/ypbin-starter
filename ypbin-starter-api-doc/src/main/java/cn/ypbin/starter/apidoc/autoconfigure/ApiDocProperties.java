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

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * API 文档配置项。
 *
 * @author wenbin
 * @since 2026-07-30
 */
@ConfigurationProperties(prefix = ApiDocProperties.PREFIX)
public class ApiDocProperties {

    public static final String PREFIX = "ypbin.api-doc";

    /** 是否启用 API 文档，默认开启 */
    private boolean enabled = true;

    /** 生产环境是否关闭 SpringDoc 端点，默认关闭 */
    private boolean disableInProd = true;

    /** 文档标题 */
    private String title = "API 文档";

    /** 文档描述 */
    private String description = "";

    /** 文档版本 */
    private String version = "1.0.0";

    /** 默认分组名称 */
    private String groupName = "default";

    /** 是否创建默认 GroupedOpenApi */
    private boolean defaultGroupEnabled = true;

    /** 是否启用 @ApiOrder 排序 */
    private boolean orderEnabled = true;

    /** 扫描路径 */
    private List<String> pathsToMatch = new ArrayList<>(List.of("/**"));

    /** 排除路径 */
    private List<String> pathsToExclude = new ArrayList<>(List.of("/error", "/actuator/**"));

    /** 扫描包 */
    private List<String> packagesToScan = new ArrayList<>();

    /** 排除包 */
    private List<String> packagesToExclude = new ArrayList<>();

    /** 全局安全请求头 */
    private List<String> securityHeaders = new ArrayList<>(List.of("Authorization", "X-Request-Id", "X-Tenant-Id", "X-Version"));

    /** 联系人信息 */
    @NestedConfigurationProperty
    private Contact contact = new Contact();

    /** 许可协议信息 */
    @NestedConfigurationProperty
    private License license = new License();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDisableInProd() {
        return disableInProd;
    }

    public void setDisableInProd(boolean disableInProd) {
        this.disableInProd = disableInProd;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public boolean isDefaultGroupEnabled() {
        return defaultGroupEnabled;
    }

    public void setDefaultGroupEnabled(boolean defaultGroupEnabled) {
        this.defaultGroupEnabled = defaultGroupEnabled;
    }

    public boolean isOrderEnabled() {
        return orderEnabled;
    }

    public void setOrderEnabled(boolean orderEnabled) {
        this.orderEnabled = orderEnabled;
    }

    public List<String> getPathsToMatch() {
        return pathsToMatch;
    }

    public void setPathsToMatch(List<String> pathsToMatch) {
        this.pathsToMatch = pathsToMatch;
    }

    public List<String> getPathsToExclude() {
        return pathsToExclude;
    }

    public void setPathsToExclude(List<String> pathsToExclude) {
        this.pathsToExclude = pathsToExclude;
    }

    public List<String> getPackagesToScan() {
        return packagesToScan;
    }

    public void setPackagesToScan(List<String> packagesToScan) {
        this.packagesToScan = packagesToScan;
    }

    public List<String> getPackagesToExclude() {
        return packagesToExclude;
    }

    public void setPackagesToExclude(List<String> packagesToExclude) {
        this.packagesToExclude = packagesToExclude;
    }

    public List<String> getSecurityHeaders() {
        return securityHeaders;
    }

    public void setSecurityHeaders(List<String> securityHeaders) {
        this.securityHeaders = securityHeaders;
    }

    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    public License getLicense() {
        return license;
    }

    public void setLicense(License license) {
        this.license = license;
    }

    /**
     * 联系人信息。
     */
    public static class Contact {

        /** 姓名 */
        private String name = "";

        /** 邮箱 */
        private String email = "";

        /** 主页地址 */
        private String url = "";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    /**
     * 许可协议信息。
     */
    public static class License {

        /** 协议名称 */
        private String name = "";

        /** 协议地址 */
        private String url = "";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }
}

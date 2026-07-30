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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * API 文档配置项。
 *
 * @author wenbin
 * @since 2026-07-30
 */
@ConfigurationProperties(prefix = "ypbin.api-doc")
public class ApiDocProperties {

    /** 是否启用 API 文档，默认开启 */
    private boolean enabled = true;

    /** 文档标题 */
    private String title = "API 文档";

    /** 文档描述 */
    private String description = "";

    /** 文档版本 */
    private String version = "1.0.0";

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

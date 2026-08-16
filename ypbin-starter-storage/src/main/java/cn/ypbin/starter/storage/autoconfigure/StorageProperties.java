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
package cn.ypbin.starter.storage.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 存储模块配置项。
 *
 * <p>支持多存储源共存：{@link #local} 与 {@link #oss} 均为列表，每项以 platform 为唯一键，
 * 运行时按 key 路由；{@link #defaultPlatform} 指定默认源。默认零配置时自动启用一个本地源。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@ConfigurationProperties(prefix = StorageProperties.PREFIX)
public class StorageProperties {

    public static final String PREFIX = "ypbin.storage";

    /** 是否启用存储模块，默认开启 */
    private boolean enabled = true;

    /** 默认存储平台标识 */
    private String defaultPlatform;

    /** 单次上传默认最大字节数，-1 不限制 */
    private long maxFileSize = -1L;

    /** 本地存储源列表 */
    private List<LocalConfig> local = new ArrayList<>();

    /** S3 兼容对象存储源列表 */
    private List<OssConfig> oss = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultPlatform() {
        return defaultPlatform;
    }

    public void setDefaultPlatform(String defaultPlatform) {
        this.defaultPlatform = defaultPlatform;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public List<LocalConfig> getLocal() {
        return local;
    }

    public void setLocal(List<LocalConfig> local) {
        this.local = local;
    }

    public List<OssConfig> getOss() {
        return oss;
    }

    public void setOss(List<OssConfig> oss) {
        this.oss = oss;
    }

    /**
     * 本地存储源配置。
     */
    public static class LocalConfig {

        /** 平台标识（唯一键） */
        private String platform;

        /** 是否启用 */
        private boolean enabled = true;

        /** 存储根目录 */
        private String basePath;

        /** 访问域名前缀（拼接生成 URL） */
        private String domain = "";

        public String getPlatform() {
            return platform;
        }

        public void setPlatform(String platform) {
            this.platform = platform;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBasePath() {
            return basePath;
        }

        public void setBasePath(String basePath) {
            this.basePath = basePath;
        }

        public String getDomain() {
            return domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }
    }

    /**
     * S3 兼容对象存储源配置。
     *
     * <p>阿里云 OSS / 腾讯云 COS / MinIO / 七牛等均兼容 S3 协议，通过 endpoint /
     * region / pathStyle 区分，无需为每家单独实现。</p>
     */
    public static class OssConfig {

        /** 平台标识（唯一键） */
        private String platform;

        /** 是否启用 */
        private boolean enabled = true;

        /** 服务端点 */
        private String endpoint;

        /** 区域 */
        private String region = "us-east-1";

        /** 访问密钥 ID */
        private String accessKey;

        /** 访问密钥 */
        private String secretKey;

        /** 桶名 */
        private String bucket;

        /** 是否使用 path-style 访问（MinIO 等需开启） */
        private boolean pathStyleAccess = true;

        /** 访问域名前缀（自定义 CDN 域名，为空则由客户端生成） */
        private String domain = "";

        public String getPlatform() {
            return platform;
        }

        public void setPlatform(String platform) {
            this.platform = platform;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public boolean isPathStyleAccess() {
            return pathStyleAccess;
        }

        public void setPathStyleAccess(boolean pathStyleAccess) {
            this.pathStyleAccess = pathStyleAccess;
        }

        public String getDomain() {
            return domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }
    }
}

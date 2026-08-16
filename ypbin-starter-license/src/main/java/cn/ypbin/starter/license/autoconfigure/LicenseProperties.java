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
package cn.ypbin.starter.license.autoconfigure;

import cn.ypbin.starter.license.extension.RemoteFailurePolicy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * License 授权配置项。
 *
 * @author wenbin
 * @since 2026-08-05
 */
@ConfigurationProperties(prefix = LicenseProperties.PREFIX)
public class LicenseProperties {

    public static final String PREFIX = "ypbin.license";

    /** 是否启用授权校验，默认开启 */
    private boolean enabled = true;

    /** SM2 公钥（Base64）：运行端仅需公钥用于验签，私钥仅在供应方签发端持有 */
    private String publicKey;

    /** SM4 密钥（Base64，16 字节）：授权文件对称加解密密钥 */
    private String secretKey;

    /** 授权文件路径：默认文件存储实现从此读取授权串 */
    private String location = "./license.dat";

    /** 是否启用机器指纹绑定校验，默认开启 */
    private boolean fingerprintEnabled = true;

    /**
     * 无授权文件时是否允许启动。
     *
     * <p>默认 {@code false}：缺授权即启动失败并暴露原因，避免「以为受保护实则裸奔」。
     * 置为 {@code true} 可在无授权文件时以「非法不可用」状态启动，受保护能力被拦截、非保护能力照常，
     * 适用于先启动后补授权的交付流程。</p>
     */
    private boolean allowStartupWithoutLicense = false;

    /** 联机校验配置：配置了服务地址才启用 {@code @LicenseCheck(online=true)} 的实时回验 */
    private Online online = new Online();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public boolean isFingerprintEnabled() {
        return fingerprintEnabled;
    }

    public void setFingerprintEnabled(boolean fingerprintEnabled) {
        this.fingerprintEnabled = fingerprintEnabled;
    }

    public boolean isAllowStartupWithoutLicense() {
        return allowStartupWithoutLicense;
    }

    public void setAllowStartupWithoutLicense(boolean allowStartupWithoutLicense) {
        this.allowStartupWithoutLicense = allowStartupWithoutLicense;
    }

    public Online getOnline() {
        return online;
    }

    public void setOnline(Online online) {
        this.online = online;
    }

    /**
     * 联机校验配置。
     *
     * <p>为消费端配置供应方的联机校验服务地址，即可启用 {@code @LicenseCheck(online=true)} 与定期联机
     * 任务的实时回验，感知远程吊销。网络不可达时采用放行+告警策略，只有服务端明确返回无效才阻断。</p>
     *
     * <p>鉴权采用接口签名：accessKey 为公开的应用标识、secretKey 为参与签名的私有密钥，二者由签发端
     * 「开放应用管理」为每个消费端应用独立签发；密钥泄露只影响单一应用，且可在应用管理禁用/重置。</p>
     */
    public static class Online {

        /** 联机校验服务根地址（如 {@code http://license-admin:8080}）；为空则不装配联机校验 */
        private String baseUrl;

        /** 开放应用 Access Key（公开标识，与签发端应用管理注册的应用一致） */
        private String accessKey;

        /** 开放应用 Secret Key（私有密钥，参与请求签名，不下发） */
        private String secretKey;

        /** 单次联机校验超时时间 */
        private Duration timeout = Duration.ofSeconds(5);

        /** 联机服务无法明确裁决时的处理策略 */
        private RemoteFailurePolicy failurePolicy = RemoteFailurePolicy.FAIL_OPEN_WITH_WARNING;

        /**
         * 联机校验缓存窗口（秒）：最近一次服务端<strong>明确返回有效</strong>后，窗口内不再重复联机校验，
         * 避免 {@code @LicenseCheck(online=true)} 每次方法调用都发 HTTP。吊销感知延迟 ≤ 缓存窗口，
         * 默认 1 小时。
         */
        private long cacheSeconds = 3600;

        /**
         * 放行窗口（秒）：网络异常/HTTP 非 200/响应解析失败/{@code valid} 字段缺失或非布尔等「放行但不明确
         * 有效」结果，进入这个短窗口，窗口内不再重复联机（防止联机服务不可用时被高频调用打爆），默认 1 分钟。
         * 与 {@link #cacheSeconds} 是两套独立窗口：只有服务端明确返回有效才用长窗口，放行永远只用这个短窗口。
         */
        private long failOpenCacheSeconds = 60;

        /**
         * 连续放行次数阈值：达到该阈值后放行窗口升级为 {@link #failOpenBackoffSeconds}（更长），
         * 默认 5 次。服务端任意一次明确返回（有效或无效）都会重置计数——只有「连续不可达/异常」才升级退避。
         */
        private int failOpenThreshold = 5;

        /**
         * 退避窗口（秒）：连续放行次数达到 {@link #failOpenThreshold} 后使用的更长窗口，默认 5 分钟，
         * 进一步降低对故障中的联机服务的调用频率。
         */
        private long failOpenBackoffSeconds = 300;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
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

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public RemoteFailurePolicy getFailurePolicy() {
            return failurePolicy;
        }

        public void setFailurePolicy(RemoteFailurePolicy failurePolicy) {
            this.failurePolicy = failurePolicy;
        }

        public long getCacheSeconds() {
            return cacheSeconds;
        }

        public void setCacheSeconds(long cacheSeconds) {
            this.cacheSeconds = cacheSeconds;
        }

        public long getFailOpenCacheSeconds() {
            return failOpenCacheSeconds;
        }

        public void setFailOpenCacheSeconds(long failOpenCacheSeconds) {
            this.failOpenCacheSeconds = failOpenCacheSeconds;
        }

        public int getFailOpenThreshold() {
            return failOpenThreshold;
        }

        public void setFailOpenThreshold(int failOpenThreshold) {
            this.failOpenThreshold = failOpenThreshold;
        }

        public long getFailOpenBackoffSeconds() {
            return failOpenBackoffSeconds;
        }

        public void setFailOpenBackoffSeconds(long failOpenBackoffSeconds) {
            this.failOpenBackoffSeconds = failOpenBackoffSeconds;
        }
    }
}

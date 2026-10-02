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
package cn.ypbin.starter.sign.autoconfigure;

import cn.ypbin.starter.sign.core.SignAlgorithm;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 接口签名配置项。
 *
 * @author wenbin
 * @since 2026-07-30
 */
@ConfigurationProperties(prefix = SignProperties.PREFIX)
public class SignProperties {

    public static final String PREFIX = "ypbin.sign";

    /** 是否启用签名校验 */
    private boolean enabled = false;

    /** 校验模式：ANNOTATION（仅 @ApiSign 接口）或 GLOBAL（全局拦截，按 skip-path 排除） */
    private Mode mode = Mode.ANNOTATION;

    /** 签名算法，默认 HMAC-SHA256 */
    private SignAlgorithm algorithm = SignAlgorithm.HMAC_SHA256;

    /** 签名有效期（秒） */
    private long timeout = 60L;

    /** 是否启用 nonce 防重放 */
    private boolean replayProtect = true;

    /**
     * 签名校验模式：REQUIRED（默认，必须带齐四件套）或 OPTIONAL（灰度期，四件套全无则放行）。
     *
     * <p><b>灰度用途</b>：开放 API 从"仅 Key"迁移到"Key + 签名"时，先置 OPTIONAL 让既有接入方
     * 不受影响，待其完成签名改造后再切 REQUIRED。</p>
     *
     * <p><b>降级防护</b>：OPTIONAL 下只有<b>四个参数全无</b>才放行；只要出现任一签名参数，
     * 就按 REQUIRED 处理（缺失其余一律拒绝）。否则攻击者可故意只带部分参数落进放行分支。</p>
     */
    private SignMode signMode = SignMode.REQUIRED;

    /**
     * 是否信任 {@code X-Forwarded-For} 头作为来源地址（IP 白名单用）。
     *
     * <p><b>默认 false</b>：该头可被客户端伪造，仅在请求确实经过可信代理时才可开启。</p>
     */
    private boolean trustForwardedHeader = false;

    /** 应用列表 */
    private List<AppInfo> apps = new ArrayList<>();

    /** GLOBAL 模式下排除的路径（Ant 风格） */
    private List<String> skipPath = new ArrayList<>();

    /** 排除参与签名的参数名 */
    private List<String> skipParamNames = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public SignAlgorithm getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(SignAlgorithm algorithm) {
        this.algorithm = algorithm;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public boolean isReplayProtect() {
        return replayProtect;
    }

    public void setReplayProtect(boolean replayProtect) {
        this.replayProtect = replayProtect;
    }

    public SignMode getSignMode() {
        return signMode;
    }

    public void setSignMode(SignMode signMode) {
        this.signMode = signMode;
    }

    public boolean isTrustForwardedHeader() {
        return trustForwardedHeader;
    }

    public void setTrustForwardedHeader(boolean trustForwardedHeader) {
        this.trustForwardedHeader = trustForwardedHeader;
    }

    public List<AppInfo> getApps() {
        return apps;
    }

    public void setApps(List<AppInfo> apps) {
        this.apps = apps;
    }

    public List<String> getSkipPath() {
        return skipPath;
    }

    public void setSkipPath(List<String> skipPath) {
        this.skipPath = skipPath;
    }

    public List<String> getSkipParamNames() {
        return skipParamNames;
    }

    public void setSkipParamNames(List<String> skipParamNames) {
        this.skipParamNames = skipParamNames;
    }

    /** 校验模式 */
    public enum Mode {
        /** 仅对 @ApiSign 标注的接口校验 */
        ANNOTATION,
        /** 全局拦截，按 skipPath 排除 */
        GLOBAL
    }

    /** 签名参数校验模式（灰度开关） */
    public enum SignMode {
        /** 必须带齐 accessKey/timestamp/nonce/sign 四件套 */
        REQUIRED,
        /**
         * 灰度期：四件套<b>全无</b>时视为"未启用签名的既有请求"而放行；
         * 只要出现任一签名参数，即按 {@link #REQUIRED} 严格校验（防降级绕过）。
         */
        OPTIONAL
    }

    /** 应用信息 */
    // 字段由 Spring Boot 在对象构造后绑定（@ConfigurationProperties），构造器结束时必然为 null
    @SuppressWarnings("NullAway.Init")
    public static class AppInfo {
        /** Access Key（访问密钥，公开标识） */
        private String accessKey;
        /** Secret Key（私有密钥，参与签名，不下发） */
        private String secretKey;
        /** 应用名称 */
        private String appName;
        /** 失效时间，为空表示永不过期 */
        private LocalDateTime expireTime;
        /** 是否启用 */
        private boolean enabled = true;
        /** 所属租户 ID（可选；为空表示不做租户校验） */
        private Long tenantId;
        /** 作用域集合（可选；为空表示不限制） */
        private List<String> scopes = new ArrayList<>();
        /** 应用级 QPS 配额（空或 <=0 表示不限） */
        private Integer rateLimitQps;
        /** 应用级日调用配额（空或 <=0 表示不限） */
        private Integer dailyQuota;
        /** 来源 IP 白名单（CIDR 逗号分隔；空表示不限来源） */
        private String ipWhitelist;

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

        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }

        public LocalDateTime getExpireTime() {
            return expireTime;
        }

        public void setExpireTime(LocalDateTime expireTime) {
            this.expireTime = expireTime;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Long getTenantId() {
            return tenantId;
        }

        public void setTenantId(Long tenantId) {
            this.tenantId = tenantId;
        }

        public List<String> getScopes() {
            return scopes;
        }

        public void setScopes(List<String> scopes) {
            this.scopes = (scopes == null) ? new ArrayList<>() : scopes;
        }

        public Integer getRateLimitQps() {
            return rateLimitQps;
        }

        public void setRateLimitQps(Integer rateLimitQps) {
            this.rateLimitQps = rateLimitQps;
        }

        public Integer getDailyQuota() {
            return dailyQuota;
        }

        public void setDailyQuota(Integer dailyQuota) {
            this.dailyQuota = dailyQuota;
        }

        public String getIpWhitelist() {
            return ipWhitelist;
        }

        public void setIpWhitelist(String ipWhitelist) {
            this.ipWhitelist = ipWhitelist;
        }

        /**
         * 是否已过期。
         *
         * @return true 已过期
         */
        public boolean isExpired() {
            return expireTime != null && LocalDateTime.now().isAfter(expireTime);
        }
    }
}

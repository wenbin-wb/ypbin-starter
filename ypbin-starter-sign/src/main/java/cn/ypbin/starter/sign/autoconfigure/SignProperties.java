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
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 接口签名配置项。
 *
 * @author wenbin
 * @since 2026-07-30
 */
@ConfigurationProperties(prefix = "ypbin.sign")
public class SignProperties {

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

    /** 应用信息 */
    public static class AppInfo {
        /** 应用 ID */
        private String appId;
        /** 应用密钥 */
        private String appSecret;
        /** 应用名称 */
        private String appName;

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }
    }
}

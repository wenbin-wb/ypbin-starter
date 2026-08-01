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
package cn.ypbin.starter.sign.core;

import java.time.LocalDateTime;

/**
 * 开放应用信息。
 *
 * <p>签名校验运行时使用的应用模型：{@link #accessKey} 为公开标识、{@link #secretKey} 为参与签名的私有密钥。
 * 由 {@link SignAppProvider} 提供，可来自配置文件或数据库。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class SignApp {

    /** Access Key（访问密钥，公开标识） */
    private String accessKey;

    /** Secret Key（私有密钥，参与签名） */
    private String secretKey;

    /** 应用名称 */
    private String appName;

    /** 失效时间，为空表示永不过期 */
    private LocalDateTime expireTime;

    /** 是否启用 */
    private boolean enabled = true;

    public SignApp() {
    }

    public SignApp(String accessKey, String secretKey) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    /**
     * 是否已过期。
     *
     * @return true 已过期
     */
    public boolean isExpired() {
        return expireTime != null && LocalDateTime.now().isAfter(expireTime);
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
}

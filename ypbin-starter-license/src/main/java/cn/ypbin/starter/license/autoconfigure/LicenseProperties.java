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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * License 授权配置项。
 *
 * @author wenbin
 * @since 2026-08-05
 */
@ConfigurationProperties(prefix = "ypbin.license")
public class LicenseProperties {

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
}

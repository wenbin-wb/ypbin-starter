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
package cn.ypbin.starter.apicrypto.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 接口加解密配置项。
 *
 * @author wenbin
 * @since 2026-07-30
 */
@ConfigurationProperties(prefix = ApiCryptoProperties.PREFIX)
public class ApiCryptoProperties {

    public static final String PREFIX = "ypbin.api-crypto";

    /** 是否启用接口加解密，默认开启（仍需方法上标注 @ApiEncrypt 才生效） */
    private boolean enabled = true;

    /** 默认 AES 实现的密钥，长度需为 16/24/32 字节。配置后才装配默认加解密器 */
    private String key;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}

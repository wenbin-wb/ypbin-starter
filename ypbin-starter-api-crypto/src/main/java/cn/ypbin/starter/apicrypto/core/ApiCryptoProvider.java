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
package cn.ypbin.starter.apicrypto.core;

/**
 * 接口加解密算法扩展点。
 *
 * <p>抽象加解密实现，业务方可用 AES / 国密 SM4 / RSA 等实现本接口并覆盖默认 Bean。
 * 默认提供 AES 实现（需配置密钥）。加解密作用于请求体 / 响应体的字符串内容。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public interface ApiCryptoProvider {

    /**
     * 加密（用于响应体）。
     *
     * @param plainText 明文
     * @return 密文
     */
    String encrypt(String plainText);

    /**
     * 解密（用于请求体）。
     *
     * @param cipherText 密文
     * @return 明文
     */
    String decrypt(String cipherText);
}

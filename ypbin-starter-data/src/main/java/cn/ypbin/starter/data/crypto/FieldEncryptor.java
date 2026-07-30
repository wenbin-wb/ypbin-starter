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
package cn.ypbin.starter.data.crypto;

/**
 * 字段加密器扩展点。
 *
 * <p>用于数据库字段的存前加密、读后解密。默认提供 AES 实现，业务方可实现本接口
 * 接入国密 SM4、KMS 等并通过 {@code @ConditionalOnMissingBean} 覆盖。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public interface FieldEncryptor {

    /**
     * 加密明文。
     *
     * @param plainText 明文
     * @return 密文
     */
    String encrypt(String plainText);

    /**
     * 解密密文。
     *
     * @param cipherText 密文
     * @return 明文
     */
    String decrypt(String cipherText);
}

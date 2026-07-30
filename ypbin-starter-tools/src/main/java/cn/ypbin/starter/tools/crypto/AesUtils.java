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
package cn.ypbin.starter.tools.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES 加解密工具。
 *
 * <p>采用 AES-GCM 认证加密模式（而非老旧的 ECB/CBC），每次加密随机生成 12 字节 IV
 * 并前置到密文，兼顾机密性与完整性。密钥支持 128/192/256 位。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public final class AesUtils {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BIT = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private AesUtils() {
    }

    /**
     * 加密并 Base64 编码。
     *
     * @param plainText 明文
     * @param key       密钥（16/24/32 字节）
     * @return Base64 密文（含前置 IV）
     */
    public static String encrypt(String plainText, byte[] key) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, ALGORITHM),
                new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            // 前置 IV：iv + cipherText
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("AES 加密失败", e);
        }
    }

    /**
     * Base64 解码并解密。
     *
     * @param cipherText Base64 密文（含前置 IV）
     * @param key        密钥
     * @return 明文
     */
    public static String decrypt(String cipherText, byte[] key) {
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText);
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, ALGORITHM),
                new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] plain = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES 解密失败", e);
        }
    }

    /**
     * 用字符串密钥加密（UTF-8 字节，长度需为 16/24/32）。
     *
     * @param plainText 明文
     * @param key       字符串密钥
     * @return Base64 密文
     */
    public static String encrypt(String plainText, String key) {
        return encrypt(plainText, key.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 用字符串密钥解密。
     *
     * @param cipherText Base64 密文
     * @param key        字符串密钥
     * @return 明文
     */
    public static String decrypt(String cipherText, String key) {
        return decrypt(cipherText, key.getBytes(StandardCharsets.UTF_8));
    }
}

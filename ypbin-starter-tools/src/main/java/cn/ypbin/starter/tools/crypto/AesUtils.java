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
import java.security.spec.KeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES 加解密工具。
 *
 * <p>采用 AES-GCM 认证加密模式（而非老旧的 ECB/CBC），每次加密随机生成 12 字节 IV 并前置到密文，
 * 兼顾机密性与完整性。密钥支持 128/192/256 位。覆盖场景：字符串/字节明文加解密、Base64 与原始字节
 * 两种密文形态、随机密钥生成与 Base64 编解码、基于口令的密钥派生（PBKDF2）。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public final class AesUtils {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BIT = 128;
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_ITERATIONS = 65536;

    private static final SecureRandom RANDOM = new SecureRandom();

    private AesUtils() {
    }

    // ------------------------------------------------------------------ 字节级核心

    /**
     * 加密字节数组，返回含前置 IV 的原始密文字节（iv + cipherText）。
     *
     * @param plain 明文字节
     * @param key   密钥（16/24/32 字节）
     * @return 密文字节（前 12 字节为 IV）
     */
    public static byte[] encryptBytes(byte[] plain, byte[] key) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, ALGORITHM),
                new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] cipherText = cipher.doFinal(plain);
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return combined;
        } catch (Exception e) {
            throw new IllegalStateException("AES 加密失败", e);
        }
    }

    /**
     * 解密含前置 IV 的原始密文字节。
     *
     * @param combined 密文字节（前 12 字节为 IV）
     * @param key      密钥
     * @return 明文字节
     */
    public static byte[] decryptBytes(byte[] combined, byte[] key) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, ALGORITHM),
                new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            return cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
        } catch (Exception e) {
            throw new IllegalStateException("AES 解密失败", e);
        }
    }

    // ------------------------------------------------------------------ 字符串 + Base64

    /**
     * 加密字符串明文并 Base64 编码。
     *
     * @param plainText 明文
     * @param key       密钥（16/24/32 字节）
     * @return Base64 密文（含前置 IV）
     */
    public static String encrypt(String plainText, byte[] key) {
        return Base64.getEncoder().encodeToString(encryptBytes(plainText.getBytes(StandardCharsets.UTF_8), key));
    }

    /**
     * Base64 解码并解密为字符串明文。
     *
     * @param cipherText Base64 密文（含前置 IV）
     * @param key        密钥
     * @return 明文
     */
    public static String decrypt(String cipherText, byte[] key) {
        return new String(decryptBytes(Base64.getDecoder().decode(cipherText), key), StandardCharsets.UTF_8);
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

    // ------------------------------------------------------------------ 密钥工具

    /**
     * 生成随机 AES 密钥。
     *
     * @param keyBits 密钥位数（128 / 192 / 256）
     * @return 密钥字节
     */
    public static byte[] generateKey(int keyBits) {
        try {
            KeyGenerator generator = KeyGenerator.getInstance(ALGORITHM);
            generator.init(keyBits, RANDOM);
            SecretKey secretKey = generator.generateKey();
            return secretKey.getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("AES 密钥生成失败", e);
        }
    }

    /**
     * 生成随机 AES 密钥并 Base64 编码，便于配置存储。
     *
     * @param keyBits 密钥位数（128 / 192 / 256）
     * @return Base64 密钥
     */
    public static String generateKeyBase64(int keyBits) {
        return Base64.getEncoder().encodeToString(generateKey(keyBits));
    }

    /**
     * 将 Base64 密钥还原为字节。
     *
     * @param base64Key Base64 密钥
     * @return 密钥字节
     */
    public static byte[] decodeKey(String base64Key) {
        return Base64.getDecoder().decode(base64Key);
    }

    /**
     * 基于口令与盐派生 AES 密钥（PBKDF2WithHmacSHA256，65536 次迭代）。
     *
     * <p>适合用用户口令而非随机密钥的场景；相同口令 + 相同盐得到相同密钥，盐需与密文一同保存。</p>
     *
     * @param password 口令
     * @param salt     盐（建议 16 字节随机值）
     * @param keyBits  派生密钥位数（128 / 192 / 256）
     * @return 派生的密钥字节
     */
    public static byte[] deriveKey(String password, byte[] salt, int keyBits) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, keyBits);
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("AES 密钥派生失败", e);
        }
    }

    /**
     * 生成指定字节数的随机盐。
     *
     * @param length 盐字节数
     * @return 随机盐
     */
    public static byte[] generateSalt(int length) {
        byte[] salt = new byte[length];
        RANDOM.nextBytes(salt);
        return salt;
    }
}

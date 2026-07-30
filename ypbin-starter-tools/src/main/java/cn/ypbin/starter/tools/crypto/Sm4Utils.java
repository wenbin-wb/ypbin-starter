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
import java.security.Security;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * 国密 SM4 对称加解密工具。
 *
 * <p>基于 BouncyCastle，采用 SM4/ECB/PKCS5Padding。密钥为 16 字节（128 位）。
 * 适用于国密合规要求下的对称加密场景。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public final class Sm4Utils {

    private static final String ALGORITHM = "SM4";
    private static final String TRANSFORMATION = "SM4/ECB/PKCS5Padding";

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private Sm4Utils() {
    }

    /**
     * SM4 加密并 Base64 编码。
     *
     * @param plainText 明文
     * @param key       16 字节密钥
     * @return Base64 密文
     */
    public static String encrypt(String plainText, byte[] key) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, ALGORITHM));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("SM4 加密失败", e);
        }
    }

    /**
     * Base64 解码并 SM4 解密。
     *
     * @param cipherText Base64 密文
     * @param key        16 字节密钥
     * @return 明文
     */
    public static String decrypt(String cipherText, byte[] key) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, ALGORITHM));
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(cipherText));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("SM4 解密失败", e);
        }
    }

    /**
     * 用字符串密钥加密（UTF-8 字节，需为 16 字节）。
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

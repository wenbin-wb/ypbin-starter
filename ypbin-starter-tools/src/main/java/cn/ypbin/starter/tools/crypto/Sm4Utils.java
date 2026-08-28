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
import java.security.Security;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * 国密 SM4 对称加解密工具。
 *
 * <p>基于 BouncyCastle，密钥固定 16 字节（128 位）。提供三种模式：
 * ECB（无 IV，兼容简单场景）、CBC（需 16 字节 IV）、GCM（认证加密，推荐，含完整性校验）。
 * 覆盖字符串/字节明文、Base64/Hex 两种密文形态、随机密钥生成与编解码。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public final class Sm4Utils {

    private static final String ALGORITHM = "SM4";
    private static final String ECB = "SM4/ECB/PKCS5Padding";
    private static final String CBC = "SM4/CBC/PKCS5Padding";
    private static final String GCM = "SM4/GCM/NoPadding";
    private static final int KEY_SIZE = 128;
    private static final int GCM_TAG_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private Sm4Utils() {
    }

    // ------------------------------------------------------------------ ECB（Base64 + 字符串，向后兼容）

    /**
     * SM4/ECB 加密并 Base64 编码。
     *
     * @param plainText 明文
     * @param key       16 字节密钥
     * @return Base64 密文
     */
    public static String encrypt(String plainText, byte[] key) {
        return Base64.getEncoder().encodeToString(encryptEcb(plainText.getBytes(StandardCharsets.UTF_8), key));
    }

    /**
     * Base64 解码并 SM4/ECB 解密。
     *
     * @param cipherText Base64 密文
     * @param key        16 字节密钥
     * @return 明文
     */
    public static String decrypt(String cipherText, byte[] key) {
        return new String(decryptEcb(Base64.getDecoder().decode(cipherText), key), StandardCharsets.UTF_8);
    }

    /**
     * 用字符串密钥 ECB 加密（UTF-8 字节，需为 16 字节）。
     *
     * @param plainText 明文
     * @param key       字符串密钥
     * @return Base64 密文
     */
    public static String encrypt(String plainText, String key) {
        return encrypt(plainText, key.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 用字符串密钥 ECB 解密。
     *
     * @param cipherText Base64 密文
     * @param key        字符串密钥
     * @return 明文
     */
    public static String decrypt(String cipherText, String key) {
        return new String(decryptEcb(Base64.getDecoder().decode(cipherText),
            key.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ 字节级：ECB / CBC / GCM

    /**
     * SM4/ECB 加密字节。
     *
     * @param plain 明文字节
     * @param key   16 字节密钥
     * @return 密文字节
     */
    public static byte[] encryptEcb(byte[] plain, byte[] key) {
        return doFinal(ECB, Cipher.ENCRYPT_MODE, plain, key, null);
    }

    /**
     * SM4/ECB 解密字节。
     *
     * @param cipher 密文字节
     * @param key    16 字节密钥
     * @return 明文字节
     */
    public static byte[] decryptEcb(byte[] cipher, byte[] key) {
        return doFinal(ECB, Cipher.DECRYPT_MODE, cipher, key, null);
    }

    /**
     * SM4/CBC 加密字节。
     *
     * @param plain 明文字节
     * @param key   16 字节密钥
     * @param iv    16 字节初始向量
     * @return 密文字节
     */
    public static byte[] encryptCbc(byte[] plain, byte[] key, byte[] iv) {
        return doFinal(CBC, Cipher.ENCRYPT_MODE, plain, key, new IvParameterSpec(iv));
    }

    /**
     * SM4/CBC 解密字节。
     *
     * @param cipher 密文字节
     * @param key    16 字节密钥
     * @param iv     16 字节初始向量
     * @return 明文字节
     */
    public static byte[] decryptCbc(byte[] cipher, byte[] key, byte[] iv) {
        return doFinal(CBC, Cipher.DECRYPT_MODE, cipher, key, new IvParameterSpec(iv));
    }

    /**
     * SM4/GCM 加密字节（认证加密，推荐）。IV 随机生成并前置到密文（iv + cipherText）。
     *
     * @param plain 明文字节
     * @param key   16 字节密钥
     * @return 密文字节（前 12 字节为 IV）
     */
    public static byte[] encryptGcm(byte[] plain, byte[] key) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(GCM, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, ALGORITHM),
                new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain);
            byte[] combined = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ct, 0, combined, iv.length, ct.length);
            return combined;
        } catch (Exception e) {
            throw new IllegalStateException("SM4-GCM 加密失败", e);
        }
    }

    /**
     * SM4/GCM 解密字节（认证加密）。密文需含前置 12 字节 IV。
     *
     * @param combined 密文字节（前 12 字节为 IV）
     * @param key      16 字节密钥
     * @return 明文字节
     */
    public static byte[] decryptGcm(byte[] combined, byte[] key) {
        try {
            byte[] iv = new byte[12];
            System.arraycopy(combined, 0, iv, 0, 12);
            Cipher cipher = Cipher.getInstance(GCM, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, ALGORITHM),
                new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(combined, 12, combined.length - 12);
        } catch (Exception e) {
            throw new IllegalStateException("SM4-GCM 解密失败", e);
        }
    }

    // ------------------------------------------------------------------ Hex 编解码便捷方法

    /**
     * SM4/ECB 加密并 Hex 编码（部分国密系统偏好 Hex 而非 Base64）。
     *
     * @param plainText 明文
     * @param key       16 字节密钥
     * @return Hex 密文（小写）
     */
    public static String encryptHex(String plainText, byte[] key) {
        return HexFormat.of().formatHex(encryptEcb(plainText.getBytes(StandardCharsets.UTF_8), key));
    }

    /**
     * Hex 解码并 SM4/ECB 解密。
     *
     * @param hexCipher Hex 密文
     * @param key       16 字节密钥
     * @return 明文
     */
    public static String decryptHex(String hexCipher, byte[] key) {
        return new String(decryptEcb(HexFormat.of().parseHex(hexCipher), key), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ 密钥工具

    /**
     * 生成随机 SM4 密钥（16 字节）。
     *
     * @return 密钥字节
     */
    public static byte[] generateKey() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance(ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            generator.init(KEY_SIZE, RANDOM);
            return generator.generateKey().getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("SM4 密钥生成失败", e);
        }
    }

    /**
     * 生成随机 SM4 密钥并 Base64 编码。
     *
     * @return Base64 密钥
     */
    public static String generateKeyBase64() {
        return Base64.getEncoder().encodeToString(generateKey());
    }

    /**
     * 生成指定字节数的随机 IV。
     *
     * @param length IV 字节数（CBC 用 16）
     * @return 随机 IV
     */
    public static byte[] generateIv(int length) {
        byte[] iv = new byte[length];
        RANDOM.nextBytes(iv);
        return iv;
    }

    /**
     * 统一的 Cipher 执行入口。
     *
     * @param transformation 算法/模式/填充
     * @param mode           {@link Cipher#ENCRYPT_MODE} 或 {@link Cipher#DECRYPT_MODE}
     * @param input          输入字节
     * @param key            16 字节密钥
     * @param iv             初始向量（ECB 传 {@code null}）
     * @return 处理结果字节
     */
    private static byte[] doFinal(String transformation, int mode, byte[] input, byte[] key, IvParameterSpec iv) {
        try {
            Cipher cipher = Cipher.getInstance(transformation, BouncyCastleProvider.PROVIDER_NAME);
            if (iv == null) {
                cipher.init(mode, new SecretKeySpec(key, ALGORITHM));
            } else {
                cipher.init(mode, new SecretKeySpec(key, ALGORITHM), iv);
            }
            return cipher.doFinal(input);
        } catch (Exception e) {
            throw new IllegalStateException("SM4 运算失败：" + transformation, e);
        }
    }
}

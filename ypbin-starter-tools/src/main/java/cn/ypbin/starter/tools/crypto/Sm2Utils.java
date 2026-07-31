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
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveGenParameterSpec;

/**
 * 国密 SM2 非对称加解密工具。
 *
 * <p>基于 BouncyCastle，采用 sm2p256v1 曲线。公钥加密、私钥解密，密文以 Base64 传输。
 * 密钥对可用 {@link #generateKeyPair()} 生成并以 Base64 持久化。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public final class Sm2Utils {

    private static final String ALGORITHM = "EC";
    private static final String CURVE = "sm2p256v1";
    private static final String TRANSFORMATION = "SM2";

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private Sm2Utils() {
    }

    /**
     * 生成 SM2 密钥对。
     *
     * @return 密钥对（Base64 编码的公钥与私钥）
     */
    public static KeyPairBase64 generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM,
                BouncyCastleProvider.PROVIDER_NAME);
            generator.initialize(new ECNamedCurveGenParameterSpec(CURVE));
            KeyPair keyPair = generator.generateKeyPair();
            String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
            String privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
            return new KeyPairBase64(publicKey, privateKey);
        } catch (Exception e) {
            throw new IllegalStateException("SM2 密钥对生成失败", e);
        }
    }

    /**
     * 公钥加密。
     *
     * @param plainText       明文
     * @param publicKeyBase64 Base64 公钥
     * @return Base64 密文
     */
    public static String encrypt(String plainText, String publicKeyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64);
            PublicKey publicKey = KeyFactory.getInstance(ALGORITHM, BouncyCastleProvider.PROVIDER_NAME)
                .generatePublic(new X509EncodedKeySpec(keyBytes));
            Cipher cipher = Cipher.getInstance(TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("SM2 加密失败", e);
        }
    }

    /**
     * 私钥解密。
     *
     * @param cipherTextBase64 Base64 密文
     * @param privateKeyBase64 Base64 私钥
     * @return 明文
     */
    public static String decrypt(String cipherTextBase64, String privateKeyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64);
            PrivateKey privateKey = KeyFactory.getInstance(ALGORITHM, BouncyCastleProvider.PROVIDER_NAME)
                .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            Cipher cipher = Cipher.getInstance(TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(cipherTextBase64));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("SM2 解密失败", e);
        }
    }

    // ------------------------------------------------------------------ 签名 / 验签（SM3withSM2）

    /**
     * 私钥签名（SM3withSM2），结果 Base64 编码。
     *
     * @param data             待签名数据
     * @param privateKeyBase64 Base64 私钥
     * @return Base64 签名值
     */
    public static String sign(String data, String privateKeyBase64) {
        try {
            Signature signature = Signature.getInstance("SM3withSM2", BouncyCastleProvider.PROVIDER_NAME);
            signature.initSign(loadPrivateKey(privateKeyBase64));
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("SM2 签名失败", e);
        }
    }

    /**
     * 公钥验签（SM3withSM2）。
     *
     * @param data            原始数据
     * @param signBase64      Base64 签名值
     * @param publicKeyBase64 Base64 公钥
     * @return 验签是否通过
     */
    public static boolean verify(String data, String signBase64, String publicKeyBase64) {
        try {
            Signature signature = Signature.getInstance("SM3withSM2", BouncyCastleProvider.PROVIDER_NAME);
            signature.initVerify(loadPublicKey(publicKeyBase64));
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signBase64));
        } catch (Exception e) {
            throw new IllegalStateException("SM2 验签失败", e);
        }
    }

    // ------------------------------------------------------------------ 密钥还原

    /**
     * 从 Base64 还原 SM2 公钥。
     *
     * @param publicKeyBase64 Base64 公钥
     * @return 公钥对象
     */
    public static PublicKey loadPublicKey(String publicKeyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64);
            return KeyFactory.getInstance(ALGORITHM, BouncyCastleProvider.PROVIDER_NAME)
                .generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalStateException("SM2 公钥还原失败", e);
        }
    }

    /**
     * 从 Base64 还原 SM2 私钥。
     *
     * @param privateKeyBase64 Base64 私钥
     * @return 私钥对象
     */
    public static PrivateKey loadPrivateKey(String privateKeyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64);
            return KeyFactory.getInstance(ALGORITHM, BouncyCastleProvider.PROVIDER_NAME)
                .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalStateException("SM2 私钥还原失败", e);
        }
    }

    /**
     * Base64 编码的 SM2 密钥对。
     *
     * @param publicKey  Base64 公钥
     * @param privateKey Base64 私钥
     */
    public record KeyPairBase64(String publicKey, String privateKey) {
    }
}

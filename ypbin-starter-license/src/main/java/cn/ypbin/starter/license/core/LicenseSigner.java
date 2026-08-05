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
package cn.ypbin.starter.license.core;

import cn.ypbin.starter.license.exception.LicenseErrorCode;
import cn.ypbin.starter.license.exception.LicenseException;
import cn.ypbin.starter.tools.crypto.Sm2Utils;
import cn.ypbin.starter.tools.crypto.Sm4Utils;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * License 签发与验签。
 *
 * <p>签发流程：{@link LicenseContent} 序列化为 JSON 载荷 → SM2 私钥对载荷字节签名 →
 * 载荷与签名封装为 {@link LicenseEnvelope} → 信封 JSON 经 SM4-GCM 加密 → Base64 输出为授权串。
 * 校验流程逆序执行，且验签直接基于解密后信封中存储的载荷原文，不重序列化。</p>
 *
 * <p>签发（持有私钥与 SM4 密钥）通常在供应方的签发端进行；运行端只持有 SM2 公钥与 SM4 密钥用于校验，
 * 无法伪造授权。任一环节失败均抛出 {@link LicenseException} 并携带具体错误码，绝不静默放行。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
public final class LicenseSigner {

    /**
     * 内联「授权码」交付模式下单条授权码的最大字符数。
     *
     * <p>授权信息较少时可将授权串作为一行配置（授权码）直接下发；超过此长度应改用授权文件交付，
     * 而非截断——截断会破坏密文完整性。文件交付模式不受此限制。</p>
     */
    public static final int MAX_AUTH_CODE_LENGTH = 256;

    private LicenseSigner() {
    }

    /**
     * 签发授权串。
     *
     * @param content          授权内容
     * @param sm2PrivateKey    Base64 SM2 私钥（签名用）
     * @param sm4KeyBase64     Base64 SM4 密钥（加密用，16 字节）
     * @return Base64 授权串
     */
    public static String issue(LicenseContent content, String sm2PrivateKey, String sm4KeyBase64) {
        String payload = LicenseJson.toJson(content);
        String signature = Sm2Utils.sign(payload, sm2PrivateKey);
        LicenseEnvelope envelope = new LicenseEnvelope(payload, signature);
        String envelopeJson = LicenseJson.toJson(envelope);
        byte[] key = Base64.getDecoder().decode(sm4KeyBase64);
        byte[] encrypted = Sm4Utils.encryptGcm(envelopeJson.getBytes(StandardCharsets.UTF_8), key);
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * 校验并解出授权内容。
     *
     * @param authCode        Base64 授权串
     * @param sm2PublicKey    Base64 SM2 公钥（验签用）
     * @param sm4KeyBase64    Base64 SM4 密钥（解密用，16 字节）
     * @return 授权内容
     * @throws LicenseException 解密失败、格式错误或验签不通过时抛出
     */
    public static LicenseContent verify(String authCode, String sm2PublicKey, String sm4KeyBase64) {
        LicenseEnvelope envelope = decrypt(authCode, sm4KeyBase64);
        boolean valid;
        try {
            valid = Sm2Utils.verify(envelope.payload(), envelope.signature(), sm2PublicKey);
        } catch (Exception e) {
            throw new LicenseException(LicenseErrorCode.LICENSE_SIGNATURE_INVALID, e);
        }
        if (!valid) {
            throw new LicenseException(LicenseErrorCode.LICENSE_SIGNATURE_INVALID);
        }
        try {
            return LicenseJson.fromJson(envelope.payload(), LicenseContent.class);
        } catch (Exception e) {
            throw new LicenseException(LicenseErrorCode.LICENSE_CORRUPTED, e);
        }
    }

    /**
     * 解密授权串还原信封。
     *
     * @param authCode     Base64 授权串
     * @param sm4KeyBase64 Base64 SM4 密钥
     * @return 授权信封
     * @throws LicenseException 解密或信封解析失败时抛出
     */
    private static LicenseEnvelope decrypt(String authCode, String sm4KeyBase64) {
        try {
            byte[] key = Base64.getDecoder().decode(sm4KeyBase64);
            byte[] encrypted = Base64.getDecoder().decode(authCode);
            byte[] decrypted = Sm4Utils.decryptGcm(encrypted, key);
            String envelopeJson = new String(decrypted, StandardCharsets.UTF_8);
            return LicenseJson.fromJson(envelopeJson, LicenseEnvelope.class);
        } catch (Exception e) {
            throw new LicenseException(LicenseErrorCode.LICENSE_CORRUPTED, e);
        }
    }
}

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
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * License 签发与验签。
 *
 * <p>签发流程（v2）：{@link LicenseContent} 序列化为 JSON 载荷字节 → Deflate 压缩 → URL 安全 Base64
 * （无填充）编码为信封载荷 → SM2 私钥对载荷字符串字节签名 → 载荷与签名封装为 {@link LicenseEnvelope}
 * （含版本号）→ 信封 JSON 经 SM4-GCM 加密 → URL 安全 Base64 输出为授权串。校验流程逆序执行，且验签
 * 直接基于解密后信封中存储的载荷原文，不重序列化。</p>
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
    public static final int MAX_AUTH_CODE_LENGTH = 768;

    /** 当前信封格式版本。旧版本（v1 明文载荷）授权串不兼容，校验时按版本号拒绝。 */
    public static final int ENVELOPE_VERSION = 2;

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
        byte[] json = LicenseJson.toJson(content).getBytes(StandardCharsets.UTF_8);
        // 载荷先压缩再编码：短小的内容变化明显小，但复杂授权（多模块/额度/扩展参数）可显著瘦身
        String payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(deflate(json));
        String signature = Sm2Utils.sign(payload, sm2PrivateKey);
        LicenseEnvelope envelope = new LicenseEnvelope(ENVELOPE_VERSION, payload, signature);
        byte[] key = Base64.getDecoder().decode(sm4KeyBase64);
        byte[] encrypted = Sm4Utils.encryptGcm(
            LicenseJson.toJson(envelope).getBytes(StandardCharsets.UTF_8), key);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
    }

    /**
     * 校验并解出授权内容。
     *
     * @param authCode        Base64 授权串
     * @param sm2PublicKey    Base64 SM2 公钥（验签用）
     * @param sm4KeyBase64    Base64 SM4 密钥（解密用，16 字节）
     * @return 授权内容
     * @throws LicenseException 解密失败、版本不符、格式错误或验签不通过时抛出
     */
    public static LicenseContent verify(String authCode, String sm2PublicKey, String sm4KeyBase64) {
        LicenseEnvelope envelope = decrypt(authCode, sm4KeyBase64);
        if (envelope.version() != ENVELOPE_VERSION) {
            throw new LicenseException(LicenseErrorCode.LICENSE_CORRUPTED,
                "不支持的授权版本：" + envelope.version() + "，请重新向供应方获取授权");
        }
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
            byte[] compressed = Base64.getUrlDecoder().decode(envelope.payload());
            String payloadJson = new String(inflate(compressed), StandardCharsets.UTF_8);
            return LicenseJson.fromJson(payloadJson, LicenseContent.class);
        } catch (LicenseException e) {
            throw e;
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
            // URL 安全解码器同样兼容标准 Base64 字母表，历史标准 Base64 授权串可解出但因版本不符被拒
            byte[] encrypted = Base64.getUrlDecoder().decode(authCode);
            byte[] decrypted = Sm4Utils.decryptGcm(encrypted, key);
            String envelopeJson = new String(decrypted, StandardCharsets.UTF_8);
            return LicenseJson.fromJson(envelopeJson, LicenseEnvelope.class);
        } catch (Exception e) {
            throw new LicenseException(LicenseErrorCode.LICENSE_CORRUPTED, e);
        }
    }

    /**
     * Deflate 压缩字节。
     *
     * @param data 原始字节
     * @return 压缩后字节
     */
    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int length = deflater.deflate(buffer);
            out.write(buffer, 0, length);
        }
        deflater.end();
        return out.toByteArray();
    }

    /**
     * Inflate 解压字节。
     *
     * @param data 压缩后字节
     * @return 原始字节
     * @throws LicenseException 数据损坏无法解压时抛出
     */
    private static byte[] inflate(byte[] data) {
        Inflater inflater = new Inflater();
        inflater.setInput(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        try {
            while (!inflater.finished()) {
                int length = inflater.inflate(buffer);
                if (length == 0) {
                    throw new LicenseException(LicenseErrorCode.LICENSE_CORRUPTED, "授权载荷解压失败");
                }
                out.write(buffer, 0, length);
            }
        } catch (DataFormatException e) {
            throw new LicenseException(LicenseErrorCode.LICENSE_CORRUPTED, e);
        } finally {
            inflater.end();
        }
        return out.toByteArray();
    }
}

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
import java.security.MessageDigest;
import java.security.Security;
import java.util.HexFormat;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * 国密 SM3 摘要工具。
 *
 * <p>基于 BouncyCastle，输出 256 位（32 字节）摘要。适用于机器指纹、数据完整性校验等不可逆场景。
 * 提供字符串/字节入参与 Hex/字节出参，另含 HMAC-SM3 消息认证。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
public final class Sm3Utils {

    private static final String ALGORITHM = "SM3";
    private static final String HMAC_ALGORITHM = "HMACSM3";

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private Sm3Utils() {
    }

    /**
     * 计算字符串的 SM3 摘要并 Hex 编码。
     *
     * @param data 原文
     * @return 64 位十六进制摘要（小写）
     */
    public static String digestHex(String data) {
        return HexFormat.of().formatHex(digest(data.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 计算字节的 SM3 摘要。
     *
     * @param data 原文字节
     * @return 32 字节摘要
     */
    public static byte[] digest(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            return digest.digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("SM3 摘要计算失败", e);
        }
    }

    /**
     * 计算 HMAC-SM3 消息认证码并 Hex 编码。
     *
     * @param data 原文
     * @param key  密钥
     * @return 64 位十六进制 MAC（小写）
     */
    public static String hmacHex(String data, byte[] key) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance(HMAC_ALGORITHM, BouncyCastleProvider.PROVIDER_NAME);
            mac.init(new javax.crypto.spec.SecretKeySpec(key, HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SM3 计算失败", e);
        }
    }
}

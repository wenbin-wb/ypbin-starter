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
package cn.ypbin.starter.sign.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 签名生成器。
 *
 * <p>将参数按键字典序拼接为 {@code k1=v1&k2=v2...} 规范串，再按算法计算签名（结果大写十六进制）：
 * <ul>
 *     <li>{@link SignAlgorithm#MD5}：规范串末尾追加 {@code &secretKey=xxx} 后做 MD5；</li>
 *     <li>{@link SignAlgorithm#HMAC_SHA256}：以 secretKey 为密钥对规范串做 HMAC-SHA256。</li>
 * </ul>
 * 空值参数不参与签名，保证与客户端一致。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public final class SignGenerator {

    private SignGenerator() {
    }

    /**
     * 生成签名。
     *
     * @param params    参与签名的参数（不含 sign 本身）
     * @param secretKey 应用私有密钥
     * @param algorithm 算法
     * @return 大写十六进制签名
     */
    public static String generate(Map<String, String> params, String secretKey, SignAlgorithm algorithm) {
        String canonical = canonicalize(params);
        return switch (algorithm) {
            case MD5 -> md5Hex(canonical + "&secretKey=" + secretKey).toUpperCase();
            case HMAC_SHA256 -> hmacSha256Hex(canonical, secretKey).toUpperCase();
        };
    }

    /**
     * 按键字典序拼接规范串，空值跳过。
     */
    private static String canonicalize(Map<String, String> params) {
        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            String value = params.get(key);
            if (value != null && !value.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append('&');
                }
                sb.append(key).append('=').append(value);
            }
        }
        return sb.toString();
    }

    private static String md5Hex(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return toHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("MD5 计算失败", e);
        }
    }

    private static String hmacSha256Hex(String text, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return toHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}

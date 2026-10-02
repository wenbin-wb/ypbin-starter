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

import cn.ypbin.starter.core.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * API Key 凭证原语（看板反哺：ypbin-iot 开放 API Key 体系的通用部分）。
 *
 * <p>只做与业务无关的四件事：安全随机串生成、HMAC-SHA256 存哈希（pepper 注入）、
 * 常量时间比对、前缀回显。租户归属、作用域白名单、配额/限流、 rows 落库一律不在这里——
 * 那些是各业务自己的语义，由调用方承担。</p>
 *
 * <p><b>安全契约</b>：明文 secret 只在生成瞬间存在，调用方负责"仅返回一次"；
 * 库内只存 {@link #hashSecret} 的输出；校验走 {@link #matches}（失败统一 false，不区分
 * 不存在/密钥错/pepper 缺失，防枚举）；日志绝不打印 secret 明文。</p>
 *
 * @author wenbin
 * @since 2026-10-02
 */
public final class ApiKeyCredentials {

    /** 生成密钥的默认随机字节数（32 字节 = 256 位熵）。 */
    public static final int SECRET_BYTE_LENGTH = 32;

    /** 生成公开标识的默认随机字节数（12 字节 = 96 位熵，定位行用不需要太高）。 */
    public static final int ACCESS_KEY_BYTE_LENGTH = 12;

    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiKeyCredentials() {
    }

    /**
     * 生成安全随机串（Base64-URL，无填充）。
     *
     * @param byteLength 随机字节数（须为正数）
     * @return 随机串（调用方自行加业务前缀，如 {@code ak_}/{@code sk_}）
     */
    public static String generateSecret(int byteLength) {
        if (byteLength <= 0) {
            throw new BusinessException("随机字节数必须为正数");
        }
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 计算存库哈希（HMAC-SHA256，hex 编码）。
     *
     * @param pepper 服务端 pepper（环境变量注入，真值不入库；为空直接拒绝，fail-closed）
     * @param secret 明文 secret（完整串，含业务前缀——哈希与校验必须用同一口径）
     * @return 64 位 hex 哈希
     */
    public static String hashSecret(String pepper, String secret) {
        if (pepper == null || pepper.isEmpty() || secret == null || secret.isEmpty()) {
            throw new BusinessException("缺少密钥 pepper 或明文，拒绝计算哈希");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC 初始化失败", ex);
        }
    }

    /**
     * 常量时间比对（防时序侧信道；失败统一 false，不区分原因，防枚举）。
     *
     * @param pepper 服务端 pepper
     * @param candidate 待校验明文
     * @param expectedHash 库内哈希
     * @return 匹配返回 {@code true}；任何缺失/ mismatch 返回 {@code false}（不抛异常）
     */
    public static boolean matches(String pepper, String candidate, String expectedHash) {
        if (pepper == null || pepper.isEmpty() || candidate == null || candidate.isBlank()
            || expectedHash == null || expectedHash.isEmpty()) {
            return false;
        }
        String actual;
        try {
            actual = hashSecret(pepper, candidate.trim());
        } catch (RuntimeException ex) {
            return false;
        }
        return MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),
            expectedHash.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * 明文前缀回显（控制台辨识用，不足以还原明文）。
     *
     * @param secret 明文 secret
     * @param prefix 原样回显的前缀（如 {@code sk_}）
     * @param visibleChars 明文可见字符数（须为正数）
     * @return 前缀 + 可见字符（如 {@code sk_ab12…} 形态由调用方定，这里只拼串）
     */
    public static String displayPrefix(String secret, String prefix, int visibleChars) {
        if (secret == null || secret.isEmpty() || visibleChars <= 0) {
            throw new BusinessException("明文与可见长度非法，拒绝生成回显前缀");
        }
        String body = secret.startsWith(prefix) ? secret.substring(prefix.length()) : secret;
        int take = Math.min(visibleChars, body.length());
        return prefix + body.substring(0, take);
    }
}

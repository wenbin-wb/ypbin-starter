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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 客户端签名工具。
 *
 * <p>供第三方对接方生成请求签名：填入业务参数与 accessKey/secretKey，自动补充 timestamp/nonce，
 * 计算 sign。生成的四件套（accessKey/timestamp/nonce/sign）随请求发送，服务端 {@link SignChecker} 校验。
 * 算法需与服务端 {@code ypbin.sign.algorithm} 一致。</p>
 *
 * <pre>{@code
 * Map<String, String> bizParams = Map.of("orderNo", "A100", "amount", "99.5");
 * Map<String, String> signed = SignClient.sign(bizParams, "ak-001", "sk-xxx", SignAlgorithm.HMAC_SHA256);
 * // signed 含 accessKey/timestamp/nonce/sign + 业务参数，随请求发送
 * }</pre>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public final class SignClient {

    private SignClient() {
    }

    /**
     * 生成带签名的完整参数集。
     *
     * @param bizParams 业务参数
     * @param accessKey 访问密钥（公开标识）
     * @param secretKey 私有密钥（参与签名）
     * @param algorithm 签名算法（与服务端一致）
     * @return 含 accessKey/timestamp/nonce/sign 与业务参数的 Map
     */
    public static Map<String, String> sign(Map<String, String> bizParams, String accessKey, String secretKey,
        SignAlgorithm algorithm) {
        Map<String, String> params = new HashMap<>();
        if (bizParams != null) {
            params.putAll(bizParams);
        }
        params.put("accessKey", accessKey);
        params.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        params.put("nonce", UUID.randomUUID().toString().replace("-", ""));
        // sign 本身不参与签名计算
        String sign = SignGenerator.generate(params, secretKey, algorithm);
        params.put("sign", sign);
        return params;
    }
}

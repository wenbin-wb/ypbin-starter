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

import cn.ypbin.starter.sign.autoconfigure.SignProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 签名校验器。
 *
 * <p>校验流程：提取四件套（accessKey/timestamp/nonce/sign）→ 校验非空 → 查应用（校验启用/未过期）→ 校验时间戳超时 →
 * 可选 nonce 防重放 → 收集参与签名的参数（Query + JSON Body，排除 sign 与配置的排除项）→
 * 服务端按应用密钥与算法重算签名 → 与客户端签名比对。请求体经 web 模块的可重复读包装，
 * 读取后不影响 Controller 再次读取。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class SignChecker {

    private static final Logger log = LoggerFactory.getLogger(SignChecker.class);

    private static final String ACCESS_KEY = "accessKey";
    private static final String TIMESTAMP = "timestamp";
    private static final String NONCE = "nonce";
    private static final String SIGN = "sign";
    private static final String JSON_TYPE = "application/json";

    /** 允许的未来时钟偏移（秒）：容忍客户端与服务端的小幅时钟不同步，但不接受明显来自未来的时间戳 */
    private static final long CLOCK_SKEW_SECONDS = 5L;

    private final SignProperties properties;
    private final NonceStore nonceStore;
    private final ObjectMapper objectMapper;
    private final SignAppProvider appProvider;

    public SignChecker(SignProperties properties, NonceStore nonceStore, ObjectMapper objectMapper,
        SignAppProvider appProvider) {
        this.properties = properties;
        this.nonceStore = nonceStore;
        // 专用副本并强制按 key 排序：嵌套对象拍平为字符串时输出确定、与配置无关，
        // 避免共享 mapper 的 key 顺序波动导致验签时对时错
        ObjectMapper base = (objectMapper != null) ? objectMapper : new ObjectMapper();
        this.objectMapper = base.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.appProvider = appProvider;
    }

    /**
     * 校验请求签名。
     *
     * @param request HTTP 请求
     * @return 校验结果
     */
    public SignResult check(HttpServletRequest request) {
        String accessKey = request.getParameter(ACCESS_KEY);
        String timestamp = request.getParameter(TIMESTAMP);
        String nonce = request.getParameter(NONCE);
        String sign = request.getParameter(SIGN);

        if (isBlank(accessKey) || isBlank(timestamp) || isBlank(nonce) || isBlank(sign)) {
            return SignResult.fail("缺少签名参数");
        }

        SignApp app = appProvider.findByAccessKey(accessKey).orElse(null);
        if (app == null) {
            return SignResult.fail("应用不存在");
        }
        if (!app.isEnabled()) {
            return SignResult.fail("应用已禁用");
        }
        if (app.isExpired()) {
            return SignResult.fail("应用已过期");
        }

        long now = System.currentTimeMillis() / 1000;
        long requestTime;
        try {
            requestTime = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return SignResult.fail("时间戳格式错误");
        }
        // 过去方向按 timeout 判过期；未来方向只容忍小幅时钟偏移（防"未来时间戳"扩大重放窗口）
        long ahead = requestTime - now;
        long behind = now - requestTime;
        if (behind > properties.getTimeout() || ahead > CLOCK_SKEW_SECONDS) {
            return SignResult.fail("签名已过期");
        }

        if (properties.isReplayProtect()) {
            String nonceKey = "ypbin:sign:nonce:" + accessKey + ":" + nonce;
            // nonce 存活必须覆盖时间戳的整个有效期末尾（requestTime + timeout）。
            // 固定 timeout+1 在时间戳偏未来时会早于时间戳失效前过期，留出重放真空期，
            // 故按请求时间戳动态计算 TTL。abs 校验已保证该值落在 [1, 2*timeout+1]，不会为负。
            long ttlSeconds = requestTime + properties.getTimeout() - now + 1;
            if (!nonceStore.tryUse(nonceKey, Duration.ofSeconds(ttlSeconds))) {
                return SignResult.fail("请求重复（nonce 已使用）");
            }
        }

        Map<String, String> params = collectParams(request);
        String expected = SignGenerator.generate(params, app.getSecretKey(), properties.getAlgorithm());
        // 恒定时间比较，避免逐字符短路带来的时序侧信道；不记录明文签名，防泄漏
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), sign.getBytes(StandardCharsets.UTF_8))) {
            log.warn("[ypbin-starter] 签名验证失败 accessKey={}", accessKey);
            return SignResult.fail("签名验证失败");
        }
        return SignResult.ok();
    }

    /**
     * 收集参与签名的参数：Query/表单参数 + JSON body 顶层字段，排除 sign 与配置排除项。
     */
    private Map<String, String> collectParams(HttpServletRequest request) {
        List<String> skip = new ArrayList<>();
        skip.add(SIGN);
        skip.addAll(properties.getSkipParamNames());

        Map<String, String> params = new HashMap<>(16);
        Enumeration<String> names = request.getParameterNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            if (skip.contains(name)) {
                continue;
            }
            String value = request.getParameter(name);
            if (value != null && !value.isEmpty()) {
                params.put(name, value);
            }
        }

        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().contains(JSON_TYPE)) {
            mergeJsonBody(request, skip, params);
        }
        return params;
    }

    @SuppressWarnings("unchecked")
    private void mergeJsonBody(HttpServletRequest request, List<String> skip, Map<String, String> params) {
        try {
            String body = new String(request.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
            if (body.isBlank()) {
                return;
            }
            Map<String, Object> json = objectMapper.readValue(body, Map.class);
            for (Map.Entry<String, Object> entry : json.entrySet()) {
                Object value = entry.getValue();
                if (skip.contains(entry.getKey()) || value == null) {
                    continue;
                }
                if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                    params.put(entry.getKey(), String.valueOf(value));
                } else {
                    params.put(entry.getKey(), objectMapper.writeValueAsString(value));
                }
            }
        } catch (Exception e) {
            log.warn("[ypbin-starter] 解析 JSON 请求体用于签名失败: {}", e.getMessage());
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

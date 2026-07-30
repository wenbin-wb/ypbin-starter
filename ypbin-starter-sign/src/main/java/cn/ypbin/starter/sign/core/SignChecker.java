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
 * <p>校验流程：提取四件套（appId/timestamp/nonce/sign）→ 校验非空 → 查应用 → 校验时间戳超时 →
 * 可选 nonce 防重放 → 收集参与签名的参数（Query + JSON Body，排除 sign 与配置的排除项）→
 * 服务端按应用密钥与算法重算签名 → 与客户端签名比对。请求体经 web 模块的可重复读包装，
 * 读取后不影响 Controller 再次读取。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class SignChecker {

    private static final Logger log = LoggerFactory.getLogger(SignChecker.class);

    private static final String APP_ID = "appId";
    private static final String TIMESTAMP = "timestamp";
    private static final String NONCE = "nonce";
    private static final String SIGN = "sign";
    private static final String JSON_TYPE = "application/json";

    private final SignProperties properties;
    private final NonceStore nonceStore;
    private final ObjectMapper objectMapper;
    private final Map<String, SignProperties.AppInfo> appIndex;

    public SignChecker(SignProperties properties, NonceStore nonceStore, ObjectMapper objectMapper) {
        this.properties = properties;
        this.nonceStore = nonceStore;
        this.objectMapper = objectMapper;
        this.appIndex = new HashMap<>();
        for (SignProperties.AppInfo app : properties.getApps()) {
            appIndex.put(app.getAppId(), app);
        }
    }

    /**
     * 校验请求签名。
     *
     * @param request HTTP 请求
     * @return 校验结果
     */
    public SignResult check(HttpServletRequest request) {
        String appId = request.getParameter(APP_ID);
        String timestamp = request.getParameter(TIMESTAMP);
        String nonce = request.getParameter(NONCE);
        String sign = request.getParameter(SIGN);

        if (isBlank(appId) || isBlank(timestamp) || isBlank(nonce) || isBlank(sign)) {
            return SignResult.fail("缺少签名参数");
        }

        SignProperties.AppInfo app = appIndex.get(appId);
        if (app == null) {
            return SignResult.fail("应用不存在");
        }

        long now = System.currentTimeMillis() / 1000;
        long requestTime;
        try {
            requestTime = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return SignResult.fail("时间戳格式错误");
        }
        if (Math.abs(now - requestTime) > properties.getTimeout()) {
            return SignResult.fail("签名已过期");
        }

        if (properties.isReplayProtect()) {
            String nonceKey = "ypbin:sign:nonce:" + appId + ":" + nonce;
            if (!nonceStore.tryUse(nonceKey, Duration.ofSeconds(properties.getTimeout() + 1))) {
                return SignResult.fail("请求重复（nonce 已使用）");
            }
        }

        Map<String, String> params = collectParams(request);
        String expected = SignGenerator.generate(params, app.getAppSecret(), properties.getAlgorithm());
        // 恒定时间比较，避免逐字符短路带来的时序侧信道；不记录明文签名，防泄漏
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), sign.getBytes(StandardCharsets.UTF_8))) {
            log.warn("[ypbin-starter] 签名验证失败 appId={}", appId);
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

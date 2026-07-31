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
package cn.ypbin.starter.tools.support;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 请求工具。
 *
 * <p>从当前 Servlet 请求上下文提取客户端信息。无 Web 上下文时安全降级返回占位值。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public final class RequestUtils {

    private static final String UNKNOWN = "unknown";

    private static final String[] IP_HEADERS = {
        "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"
    };

    private RequestUtils() {
    }

    /**
     * 获取当前请求。
     *
     * @return {@link HttpServletRequest}，无上下文时为 {@code null}
     */
    public static HttpServletRequest getRequest() {
        try {
            if (RequestContextHolder
                .getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                return attrs.getRequest();
            }
        } catch (Exception ignored) {
            // 无 Web 上下文
        }
        return null;
    }

    /**
     * 获取客户端 IP。
     *
     * @return 客户端 IP，无上下文时返回 {@code unknown}
     */
    public static String getClientIp() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return UNKNOWN;
        }
        for (String header : IP_HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !UNKNOWN.equalsIgnoreCase(value)) {
                int comma = value.indexOf(',');
                return (comma > 0) ? value.substring(0, comma).trim() : value.trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * 获取当前响应对象。
     *
     * @return {@link HttpServletResponse}，无上下文时为 {@code null}
     */
    public static HttpServletResponse getResponse() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                return attrs.getResponse();
            }
        } catch (Exception ignored) {
            // 无 Web 上下文
        }
        return null;
    }

    /**
     * 获取 User-Agent。
     *
     * @return UA 字符串，无上下文时返回 {@code unknown}
     */
    public static String getUserAgent() {
        return getHeader("User-Agent");
    }

    /**
     * 获取指定请求头。
     *
     * @param name 头名称
     * @return 头值，不存在或无上下文时返回 {@code unknown}
     */
    public static String getHeader(String name) {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return UNKNOWN;
        }
        String value = request.getHeader(name);
        return (value == null || value.isBlank()) ? UNKNOWN : value;
    }

    /**
     * 获取全部请求头（名称小写）。
     *
     * @return 请求头映射，无上下文时为空 Map
     */
    public static Map<String, String> getHeaders() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return Collections.emptyMap();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names != null) {
            while (names.hasMoreElements()) {
                String name = names.nextElement();
                headers.put(name.toLowerCase(), request.getHeader(name));
            }
        }
        return headers;
    }

    /**
     * 获取指定请求参数。
     *
     * @param name 参数名
     * @return 参数值，不存在或无上下文时为 {@code null}
     */
    public static String getParameter(String name) {
        HttpServletRequest request = getRequest();
        return request == null ? null : request.getParameter(name);
    }

    /**
     * 获取全部请求参数（多值取第一个）。
     *
     * @return 参数映射，无上下文时为空 Map
     */
    public static Map<String, String> getParameters() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return Collections.emptyMap();
        }
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((k, v) -> params.put(k, v.length > 0 ? v[0] : null));
        return params;
    }

    /**
     * 获取请求方法（GET/POST 等）。
     *
     * @return 方法名，无上下文时返回 {@code unknown}
     */
    public static String getMethod() {
        HttpServletRequest request = getRequest();
        return request == null ? UNKNOWN : request.getMethod();
    }

    /**
     * 获取请求 URI（不含查询串）。
     *
     * @return 请求 URI，无上下文时返回 {@code unknown}
     */
    public static String getRequestUri() {
        HttpServletRequest request = getRequest();
        return request == null ? UNKNOWN : request.getRequestURI();
    }

    /**
     * 判断是否为 Ajax 请求（X-Requested-With 为 XMLHttpRequest）。
     *
     * @return 是否 Ajax
     */
    public static boolean isAjax() {
        HttpServletRequest request = getRequest();
        return request != null && "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"));
    }
}

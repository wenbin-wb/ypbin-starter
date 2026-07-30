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
}

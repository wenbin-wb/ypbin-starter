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
package cn.ypbin.starter.web.util;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/**
 * HTTP 请求上下文静态工具。
 *
 * <p>基于 {@link RequestContextHolder} 提供当前请求、请求头、请求参数、客户端 IP 与上传文件的
 * 统一读取入口。适合在非 Controller 组件（过滤器、AOP 切面、事件监听）或不愿为工具方法而
 * 继承基类的 Controller 中使用；Controller 内能通过参数注入获取的内容（如
 * {@code @RequestHeader}）仍优先用参数注入。</p>
 *
 * @author wenbin
 * @since 2026-08-31
 */
public final class WebRequestUtils {

    private WebRequestUtils() {
    }

    /**
     * 获取当前 HTTP 请求。
     *
     * @return 当前请求
     */
    public static HttpServletRequest request() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        throw new IllegalStateException("当前线程不存在 HTTP 请求上下文");
    }

    /**
     * 当前请求路径。
     *
     * @return 请求 URI
     */
    public static String path() {
        return request().getRequestURI();
    }

    /**
     * 当前请求方法。
     *
     * @return HTTP 方法
     */
    public static String method() {
        return request().getMethod();
    }

    /**
     * 获取请求头。
     *
     * @param name 请求头名
     * @return 请求头值
     */
    public static String header(String name) {
        return request().getHeader(name);
    }

    /**
     * 获取请求头，缺失时返回默认值。
     *
     * @param name         请求头名
     * @param defaultValue 默认值
     * @return 请求头值
     */
    public static String header(String name, String defaultValue) {
        String value = header(name);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    /**
     * 获取全部请求头。
     *
     * @return 请求头 Map
     */
    public static Map<String, String> headers() {
        HttpServletRequest req = request();
        Enumeration<String> names = req.getHeaderNames();
        if (names == null) {
            return Collections.emptyMap();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, req.getHeader(name));
        }
        return headers;
    }

    /**
     * 获取请求参数。
     *
     * @param name 参数名
     * @return 参数值
     */
    public static String param(String name) {
        return request().getParameter(name);
    }

    /**
     * 获取请求参数，缺失时返回默认值。
     *
     * @param name         参数名
     * @param defaultValue 默认值
     * @return 参数值
     */
    public static String param(String name, String defaultValue) {
        String value = param(name);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    /**
     * 获取客户端 IP，优先读取常见代理头。
     *
     * @return 客户端 IP
     */
    public static String ip() {
        HttpServletRequest req = request();
        String ip = firstNonBlank(
            req.getHeader("X-Forwarded-For"),
            req.getHeader("X-Real-IP"),
            req.getHeader("Proxy-Client-IP"),
            req.getHeader("WL-Proxy-Client-IP")
        );
        if (ip == null) {
            return req.getRemoteAddr();
        }
        int comma = ip.indexOf(',');
        return comma < 0 ? ip.trim() : ip.substring(0, comma).trim();
    }

    /**
     * 获取单个上传文件。
     *
     * @param name 表单字段名
     * @return 上传文件
     */
    public static MultipartFile file(String name) {
        if (request() instanceof MultipartHttpServletRequest multipartRequest) {
            return multipartRequest.getFile(name);
        }
        return null;
    }

    /**
     * 获取多个上传文件。
     *
     * @param name 表单字段名
     * @return 上传文件列表
     */
    public static List<MultipartFile> files(String name) {
        if (request() instanceof MultipartHttpServletRequest multipartRequest) {
            return multipartRequest.getFiles(name);
        }
        return List.of();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return null;
    }
}

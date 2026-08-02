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
package cn.ypbin.starter.log.support;

import cn.ypbin.starter.log.core.IpLocationResolver;
import cn.ypbin.starter.log.core.LogClientProvider;
import cn.ypbin.starter.log.core.LogUserProvider;
import cn.ypbin.starter.log.enums.Include;
import cn.ypbin.starter.log.model.LogRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;

/**
 * 日志信息采集器。
 *
 * <p>从当前 Servlet 请求按 {@link Include} 集合选择性采集请求元信息、客户端信息与操作人。
 * 采集在无 Web 上下文（如异步、定时任务）时安全降级，不抛异常。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class LogCollector {

    private static final Logger log = LoggerFactory.getLogger(LogCollector.class);

    private final LogUserProvider userProvider;
    private final LogClientProvider clientProvider;
    private final IpLocationResolver ipLocationResolver;
    private final ObjectMapper objectMapper;

    public LogCollector(LogUserProvider userProvider, ObjectMapper objectMapper) {
        this(userProvider, Optional::empty, ip -> null, objectMapper);
    }

    public LogCollector(LogUserProvider userProvider, LogClientProvider clientProvider, ObjectMapper objectMapper) {
        this(userProvider, clientProvider, ip -> null, objectMapper);
    }

    public LogCollector(LogUserProvider userProvider, LogClientProvider clientProvider,
        IpLocationResolver ipLocationResolver, ObjectMapper objectMapper) {
        this.userProvider = userProvider;
        this.clientProvider = clientProvider;
        this.ipLocationResolver = ipLocationResolver;
        this.objectMapper = objectMapper;
    }

    /**
     * 采集日志信息。
     *
     * @param record   日志记录（原地填充）
     * @param includes 采集项集合
     * @param args     方法入参（用于采集 JSON 请求体，绕开 Servlet 流只能读一次的限制）
     * @param result   方法返回值（用于响应体，可空）
     * @param error    异常（可空）
     */
    public void collect(LogRecord record, Set<Include> includes, Object[] args, Object result, Throwable error) {
        userProvider.getCurrentUserId().ifPresent(record::setUserId);

        if (includes.contains(Include.CLIENT)) {
            clientProvider.getCurrentClient().ifPresent(client -> {
                record.setClientId(client.clientId());
                record.setClientType(client.clientType());
                record.setAuthType(client.authType());
            });
        }

        HttpServletRequest request = currentRequest();
        if (request != null) {
            record.setRequestMethod(request.getMethod());
            record.setRequestUri(request.getRequestURI());
            if (includes.contains(Include.IP)) {
                String ip = resolveIp(request);
                record.setIp(ip);
                // 归属地：接入 IpLocationResolver 时填充，未接入返回 null（字段留空）
                try {
                    record.setLocation(ipLocationResolver.resolve(ip));
                } catch (Exception e) {
                    log.debug("[ypbin-starter] IP 归属地解析失败: {}", e.getMessage());
                }
            }
            if (includes.contains(Include.REQUEST_PARAM)) {
                record.setRequestParam(resolveParams(request));
            }
            if (includes.contains(Include.REQUEST_HEADERS)) {
                record.setRequestHeaders(resolveHeaders(request));
            }
            String userAgent = request.getHeader("User-Agent");
            if (includes.contains(Include.BROWSER)) {
                record.setBrowser(userAgent);
            }
            if (includes.contains(Include.OS)) {
                record.setOs(userAgent);
            }
        }

        // 请求体从 AOP 入参序列化：Servlet 流只能读一次，@RequestBody 的 JSON 从 request 里读不到
        if (includes.contains(Include.REQUEST_BODY)) {
            record.setRequestBody(serializeArgs(args));
        }
        // 响应体从方法返回值序列化
        if (includes.contains(Include.RESPONSE_BODY) && result != null) {
            record.setResponseBody(serialize(result));
        }
    }

    private HttpServletRequest currentRequest() {
        try {
            if (RequestContextHolder
                .getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                return attrs.getRequest();
            }
        } catch (Exception ignored) {
            // 无 Web 上下文，安全降级
        }
        return null;
    }

    private String resolveIp(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String header : headers) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                int comma = value.indexOf(',');
                return (comma > 0) ? value.substring(0, comma).trim() : value.trim();
            }
        }
        return request.getRemoteAddr();
    }

    private String resolveParams(HttpServletRequest request) {
        Map<String, String[]> params = request.getParameterMap();
        if (params.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        params.forEach((key, values) -> {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(key).append('=').append(String.join(",", values));
        });
        return sb.toString();
    }

    private Map<String, String> resolveHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return Collections.emptyMap();
        }
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }

    /**
     * 序列化方法入参为 JSON，剔除无法/不宜序列化的特殊参数
     * （Servlet 请求响应、文件上传等），保留业务 DTO（含 @RequestBody 的 JSON 对象）。
     */
    private String serializeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        List<Object> loggable = Arrays.stream(args)
            .filter(this::isLoggable)
            .toList();
        if (loggable.isEmpty()) {
            return null;
        }
        // 单参数直接序列化该对象，多参数序列化为数组
        return serialize(loggable.size() == 1 ? loggable.get(0) : loggable);
    }

    private boolean isLoggable(Object arg) {
        if (arg == null) {
            return false;
        }
        return !(arg instanceof HttpServletRequest)
            && !(arg instanceof HttpServletResponse)
            && !(arg instanceof MultipartFile)
            && !(arg instanceof MultipartFile[])
            && !(arg instanceof HttpSession)
            && !(arg instanceof MultipartRequest)
            && !(arg instanceof InputStream)
            && !(arg instanceof OutputStream);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.debug("[ypbin-starter] log payload serialize failed: {}", e.getMessage());
            return null;
        }
    }
}

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
package cn.ypbin.starter.log.aspect;

import cn.ypbin.starter.log.autoconfigure.AccessLogProperties;
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
import java.util.Locale;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.databind.ObjectMapper;

/**
 * 全量访问日志切面（分块日志）。
 *
 * <p>环绕拦截控制器方法，打印请求/响应分块日志：方法/URI/参数（JSON）、逐请求头（敏感头掩码）、
 * IP、响应体、耗时。与基于 {@code @Log} 注解的操作日志互补：后者精准采集业务操作（可落库），
 * 前者是调试友好的全量请求流水（打印到日志）。切面能拿到方法返回值，故可输出 {@code ===Result===}。</p>
 *
 * <p>不标注 {@code @Component}，由 {@code AccessLogAutoConfiguration} 的 {@code @Bean} 方法创建，
 * 便于 {@code @ConditionalOnMissingBean} 覆盖（同 {@code LogAspect} 的注册方式）。</p>
 *
 * @author wenbin
 * @since 2026-08-06
 */
@Aspect
public class AccessLogAspect {

    private static final Logger log = LoggerFactory.getLogger("ypbin.access");
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    /** 响应体日志最大字符数，超出截断（避免大响应体撑爆日志文件） */
    private static final int MAX_RESULT_LENGTH = 2000;

    private final ObjectMapper objectMapper;
    private final AccessLogProperties properties;

    public AccessLogAspect(ObjectMapper objectMapper, AccessLogProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 环绕控制器方法：无 Web 上下文或命中排除路径时直接放行；否则打印请求块、执行、打印响应块。
     *
     * <p>切入点匹配 {@code @RestController}（覆盖绝大多数 API 控制器）以及方法级
     * {@code @ResponseBody}（覆盖普通 {@code @Controller} 上仅部分方法直返 JSON 的场景）。
     * 不追加 {@code @within(Controller)} 分支——AspectJ 对 {@code || @within(Controller)} 的组合匹配会抛
     * {@code Type referred to is not an annotation type}，导致整个切入点不生效（实测确认）。</p>
     */
    @Around("@within(org.springframework.web.bind.annotation.RestController)"
        + " || @annotation(org.springframework.web.bind.annotation.ResponseBody)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return point.proceed();
        }
        HttpServletRequest request = attributes.getRequest();
        String uri = request.getRequestURI();
        if (isExcluded(uri)) {
            return point.proceed();
        }

        long start = System.currentTimeMillis();
        String method = request.getMethod();
        MethodSignature signature = (MethodSignature) point.getSignature();
        // 请求块
        log.info("================  Request Start  ================");
        log.info("===Handler===  {}.{}", signature.getDeclaringType().getSimpleName(), signature.getName());
        log.info("===> {}: {} Parameters: {}", method, uri, buildParams(point));
        log.info("===Headers===");
        for (Map.Entry<String, String> header : resolveHeaders(request).entrySet()) {
            log.info("  {}: {}", header.getKey(), maskIfSensitive(header.getKey(), header.getValue()));
        }
        log.info("===IP===  {}", resolveIp(request));
        log.info("================   Request End   ================");
        log.info("");

        try {
            Object result = point.proceed();
            long cost = System.currentTimeMillis() - start;
            // 响应块
            log.info("================  Response Start  ================");
            log.info("===Result===  {}", serializeResult(result));
            log.info("<=== {}: {} ({} ms)", method, uri, cost);
            log.info("================   Response End   ================");
            return result;
        } catch (Throwable t) {
            long cost = System.currentTimeMillis() - start;
            log.info("================  Response Start  ================");
            log.info("===Result===  exception: {}", t.getMessage());
            log.info("<=== {}: {} ({} ms)", method, uri, cost);
            log.info("================   Response End   ================");
            throw t;
        }
    }

    /**
     * 是否命中排除路径（静态资源、健康检查等）。
     *
     * @param uri 请求路径
     * @return 命中排除路径返回 true
     */
    private boolean isExcluded(String uri) {
        return properties.getExcludePathPatterns().stream()
            .anyMatch(pattern -> PATH_MATCHER.match(pattern, uri));
    }

    /**
     * 序列化方法入参为参数名 → 值的 JSON（如 {@code {"current":1,"size":10}}），剔除无法序列化的特殊参数
     * （Servlet 请求响应、文件上传等）。参数名不可得时单参数直序列化、多参数序列化为数组。
     *
     * @param point 连接点
     * @return JSON 字符串；无入参或全部被过滤时返回 {@code {}}
     */
    String buildParams(ProceedingJoinPoint point) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        String[] names = signature.getParameterNames();
        List<Object> loggable = Arrays.stream(point.getArgs()).filter(AccessLogAspect::isLoggable).toList();
        if (loggable.isEmpty()) {
            return "{}";
        }
        if (names == null || names.length == 0) {
            // 参数名不可得：单参直序列化 / 多参序列化为数组
            Object target = loggable.size() == 1 ? loggable.getFirst() : loggable;
            String json = serialize(target);
            return json == null ? "{}" : json;
        }
        Map<String, Object> params = new LinkedHashMap<>();
        Object[] args = point.getArgs();
        for (int i = 0; i < args.length; i++) {
            if (isLoggable(args[i])) {
                params.put(i < names.length ? names[i] : "arg" + i, args[i]);
            }
        }
        String json = serialize(params);
        return json == null ? "{}" : json;
    }

    /**
     * 收集全部请求头为 {@code LinkedHashMap}。
     *
     * @param request 当前请求
     * @return 头名 → 值
     */
    static Map<String, String> resolveHeaders(HttpServletRequest request) {
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
     * 敏感请求头掩码：头名小写包含任一 {@link AccessLogProperties#getMaskHeaders()} 关键字时值替换为掩码。
     *
     * @param name  头名
     * @param value 原始值
     * @return 掩码后值
     */
    String maskIfSensitive(String name, String value) {
        String lower = name.toLowerCase(Locale.ROOT);
        for (String mask : properties.getMaskHeaders()) {
            if (lower.contains(mask.toLowerCase(Locale.ROOT))) {
                return "******";
            }
        }
        return value;
    }

    /**
     * 解析客户端 IP：优先 {@code X-Forwarded-For} / {@code X-Real-IP}（取首个），否则取 {@code remoteAddr}。
     *
     * @param request 当前请求
     * @return IP 地址
     */
    static String resolveIp(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP"};
        for (String header : headers) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                int comma = value.indexOf(',');
                return (comma > 0) ? value.substring(0, comma).trim() : value.trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * 剔除无法/不宜序列化的特殊参数（Servlet 请求响应、文件上传等），保留业务 DTO。
     *
     * @param arg 参数
     * @return 可序列化返回 true
     */
    private static boolean isLoggable(Object arg) {
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
            && !(arg instanceof OutputStream)
            && !(arg instanceof Resource)
            && !(arg instanceof byte[])
            && !(arg instanceof StreamingResponseBody);
    }

    /**
     * 序列化返回值：大体积/流式类型（文件下载、二进制流等）只打摘要，避免整段读入内存打进日志、
     * 拖垮磁盘 IO，甚至消费掉只能读一次的流导致响应异常；其余类型正常 JSON 序列化后按长度截断。
     *
     * @param result 方法返回值
     * @return 摘要或截断后的 JSON 字符串
     */
    private String serializeResult(Object result) {
        String summary = summarize(result);
        return summary != null ? summary : truncate(serialize(result));
    }

    /**
     * 流/二进制/资源类型的简短摘要，不读取、不消费原始内容。
     *
     * @param value 待判断对象
     * @return 摘要；不属于该类别返回 null
     */
    private static String summarize(Object value) {
        if (value instanceof Resource resource) {
            String name = resource.getFilename();
            return "<Resource: " + (name != null ? name : resource.getDescription()) + ">";
        }
        if (value instanceof byte[] bytes) {
            return "<byte[" + bytes.length + "]>";
        }
        if (value instanceof InputStream) {
            return "<InputStream>";
        }
        if (value instanceof OutputStream) {
            return "<OutputStream>";
        }
        if (value instanceof StreamingResponseBody) {
            return "<StreamingResponseBody>";
        }
        return null;
    }

    /**
     * 超长内容截断，避免单条巨型 JSON（如大数组、超长文本字段）撑爆日志文件。
     *
     * @param json 原始字符串
     * @return 截断后的字符串；未超长或为 null 原样返回
     */
    private static String truncate(String json) {
        if (json == null || json.length() <= MAX_RESULT_LENGTH) {
            return json;
        }
        return json.substring(0, MAX_RESULT_LENGTH) + "...(truncated, total " + json.length() + " chars)";
    }

    /**
     * Jackson 序列化；失败（如含不可序列化对象）时 debug 告警并返回 null。
     *
     * @param value 待序列化对象
     * @return JSON 字符串或 null
     */
    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.debug("[ypbin-starter] access log serialize failed: {}", e.getMessage());
            return null;
        }
    }
}

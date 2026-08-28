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
package cn.ypbin.starter.crud.controller;

import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.exception.ErrorCode;
import cn.ypbin.starter.core.model.R;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/**
 * 控制器基础辅助类。
 *
 * <p>本类只提供请求、当前用户、统一响应与文件参数读取等轻量封装，不声明任何路由，也不绑定具体
 * 业务模型。业务接口可直接继承本类，按自己的接口形态编写方法；标准增删改查场景再继承
 * {@link CrudController}。</p>
 *
 * <p>当前用户方法会在引入 security 模块时读取 {@code UserContext}，未引入或未登录时返回空，避免
 * extension-crud 与 security 强耦合。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public abstract class BaseController {

    private static final String USER_CONTEXT_CLASS = "cn.ypbin.starter.security.core.UserContext";

    /**
     * 获取当前 HTTP 请求。
     *
     * @return 当前请求
     */
    protected HttpServletRequest request() {
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
    protected String path() {
        return request().getRequestURI();
    }

    /**
     * 当前请求方法。
     *
     * @return HTTP 方法
     */
    protected String method() {
        return request().getMethod();
    }

    /**
     * 获取请求头。
     *
     * @param name 请求头名
     * @return 请求头值
     */
    protected String header(String name) {
        return request().getHeader(name);
    }

    /**
     * 获取请求头，缺失时返回默认值。
     *
     * @param name         请求头名
     * @param defaultValue 默认值
     * @return 请求头值
     */
    protected String header(String name, String defaultValue) {
        String value = header(name);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    /**
     * 获取全部请求头。
     *
     * @return 请求头 Map
     */
    protected Map<String, String> headers() {
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
    protected String param(String name) {
        return request().getParameter(name);
    }

    /**
     * 获取请求参数，缺失时返回默认值。
     *
     * @param name         参数名
     * @param defaultValue 默认值
     * @return 参数值
     */
    protected String param(String name, String defaultValue) {
        String value = param(name);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    /**
     * 获取客户端 IP，优先读取常见代理头。
     *
     * @return 客户端 IP
     */
    protected String ip() {
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
    protected MultipartFile file(String name) {
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
    protected List<MultipartFile> files(String name) {
        if (request() instanceof MultipartHttpServletRequest multipartRequest) {
            return multipartRequest.getFiles(name);
        }
        return List.of();
    }

    /**
     * 是否已登录。未引入 security 模块时返回 false。
     *
     * @return 登录状态
     */
    protected boolean isLogin() {
        return invokeUserContext("isLogin", Boolean.class).orElse(false);
    }

    /**
     * 当前登录用户 ID。未登录或未引入 security 模块时为空。
     *
     * @return 用户 ID
     */
    protected Optional<Long> userId() {
        return invokeUserContextOptional("getUserIdSafely", Long.class);
    }

    /**
     * 当前登录用户 ID，未登录时抛出业务异常。
     *
     * @return 用户 ID
     */
    protected Long currentUserId() {
        return userId().orElseThrow(() -> new BusinessException("当前用户未登录"));
    }

    /**
     * 当前登录用户名。未登录、未写入登录用户或未引入 security 模块时为空。
     *
     * @return 用户名
     */
    protected Optional<String> username() {
        return invokeUserContextOptional("getUsername", String.class);
    }

    /**
     * 当前登录用户名，未登录时抛出业务异常。
     *
     * @return 用户名
     */
    protected String currentUsername() {
        return username().orElseThrow(() -> new BusinessException("当前用户未登录"));
    }

    /**
     * 当前登录用户所属租户 ID。未登录、未写入登录用户或未引入 security 模块时为空。
     *
     * @return 租户 ID
     */
    protected Optional<Long> tenantId() {
        return invokeUserContextOptional("getTenantId", Long.class);
    }

    /**
     * 当前登录用户所属租户 ID，未指定租户时抛出业务异常。
     *
     * @return 租户 ID
     */
    protected Long currentTenantId() {
        return tenantId().orElseThrow(() -> new BusinessException("无法确定当前租户"));
    }

    /**
     * 成功响应（无数据）。
     *
     * @return 响应体
     */
    protected R<Void> ok() {
        return R.ok();
    }

    /**
     * 成功响应（带数据）。
     *
     * @param data 数据
     * @param <T>  数据类型
     * @return 响应体
     */
    protected <T> R<T> ok(T data) {
        return R.ok(data);
    }

    /**
     * 成功响应（带提示与数据）。
     *
     * @param message 提示
     * @param data    数据
     * @param <T>     数据类型
     * @return 响应体
     */
    protected <T> R<T> ok(String message, T data) {
        return R.ok(message, data);
    }

    /**
     * 成功响应别名，适合更强调返回数据的场景。
     *
     * @param data 数据
     * @param <T>  数据类型
     * @return 响应体
     */
    protected <T> R<T> data(T data) {
        return ok(data);
    }

    /**
     * 成功响应别名，适合更强调返回数据的场景。
     *
     * @param message 提示
     * @param data    数据
     * @param <T>     数据类型
     * @return 响应体
     */
    protected <T> R<T> data(String message, T data) {
        return ok(message, data);
    }

    /**
     * 成功响应别名。
     *
     * @return 响应体
     */
    protected R<Void> success() {
        return ok();
    }

    /**
     * 成功响应别名。
     *
     * @param message 提示
     * @return 响应体
     */
    protected R<Void> success(String message) {
        return ok(message, null);
    }

    /**
     * 按布尔结果返回成功或失败响应。
     *
     * @param flag 操作结果
     * @return 响应体
     */
    protected R<Void> status(boolean flag) {
        return flag ? ok() : fail();
    }

    /**
     * 失败响应。
     *
     * @return 响应体
     */
    protected R<Void> fail() {
        return R.fail();
    }

    /**
     * 失败响应。
     *
     * @param message 提示
     * @return 响应体
     */
    protected R<Void> fail(String message) {
        return R.fail(message);
    }

    /**
     * 失败响应。
     *
     * @param code    状态码
     * @param message 提示
     * @return 响应体
     */
    protected R<Void> fail(int code, String message) {
        return R.fail(code, message);
    }

    /**
     * 失败响应。
     *
     * @param errorCode 错误码
     * @return 响应体
     */
    protected R<Void> fail(ErrorCode errorCode) {
        return R.fail(errorCode);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                return value;
            }
        }
        return null;
    }

    private <T> Optional<T> invokeUserContextOptional(String methodName, Class<T> valueType) {
        Optional<Optional> result = invokeUserContext(methodName, Optional.class);
        if (result.isEmpty() || result.get().isEmpty()) {
            return Optional.empty();
        }
        Object value = result.get().get();
        return valueType.isInstance(value) ? Optional.of(valueType.cast(value)) : Optional.empty();
    }

    private <T> Optional<T> invokeUserContext(String methodName, Class<T> returnType) {
        try {
            Class<?> type = Class.forName(USER_CONTEXT_CLASS);
            Method method = type.getMethod(methodName);
            Object value = method.invoke(null);
            return returnType.isInstance(value) ? Optional.of(returnType.cast(value)) : Optional.empty();
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return Optional.empty();
        }
    }
}

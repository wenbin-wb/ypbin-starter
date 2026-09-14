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
package cn.ypbin.starter.log.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * 操作日志记录。
 *
 * <p>一次请求采集到的完整信息，交由 {@code LogDao} 持久化。字段按 {@code Include}
 * 采集配置选择性填充，未采集项为 {@code null}。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
// 字段由采集器逐项 setter 填充（构造后才赋值），属数据装配语义，故按类抑制 NullAway.Init
@SuppressWarnings("NullAway.Init")
public class LogRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 日志描述 */
    private String description;

    /** 所属模块 */
    private String module;

    /** 请求方法（GET/POST 等） */
    private String requestMethod;

    /** 请求 URI */
    private String requestUri;

    /** 请求头 */
    @Nullable
    private Map<String, String> requestHeaders;

    /** 请求参数 */
    @Nullable
    private String requestParam;

    /** 请求体 */
    @Nullable
    private String requestBody;

    /** 响应头 */
    private Map<String, String> responseHeaders;

    /** 响应体 */
    @Nullable
    private String responseBody;

    /** HTTP 状态码 */
    private Integer statusCode;

    /** 客户端 IP */
    private String ip;

    /** IP 归属地 */
    @Nullable
    private String location;

    /** 浏览器 */
    @Nullable
    private String browser;

    /** 操作系统 */
    @Nullable
    private String os;

    /** 客户端 ID */
    private String clientId;

    /** 客户端类型 */
    private String clientType;

    /** 认证方式 */
    private String authType;

    /** 操作人 ID */
    private Long userId;

    /** 请求开始时间 */
    private Instant timestamp;

    /** 耗时（毫秒） */
    private long timeTakenMillis;

    /** 是否成功 */
    private boolean success;

    /** 错误信息 */
    @Nullable
    private String errorMsg;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }

    public String getRequestUri() {
        return requestUri;
    }

    public void setRequestUri(String requestUri) {
        this.requestUri = requestUri;
    }

    @Nullable
    public Map<String, String> getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(@Nullable Map<String, String> requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    @Nullable
    public String getRequestParam() {
        return requestParam;
    }

    public void setRequestParam(@Nullable String requestParam) {
        this.requestParam = requestParam;
    }

    @Nullable
    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(@Nullable String requestBody) {
        this.requestBody = requestBody;
    }

    public Map<String, String> getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(Map<String, String> responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    @Nullable
    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(@Nullable String responseBody) {
        this.responseBody = responseBody;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    @Nullable
    public String getLocation() {
        return location;
    }

    public void setLocation(@Nullable String location) {
        this.location = location;
    }

    @Nullable
    public String getBrowser() {
        return browser;
    }

    public void setBrowser(@Nullable String browser) {
        this.browser = browser;
    }

    @Nullable
    public String getOs() {
        return os;
    }

    public void setOs(@Nullable String os) {
        this.os = os;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientType() {
        return clientType;
    }

    public void setClientType(String clientType) {
        this.clientType = clientType;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public long getTimeTakenMillis() {
        return timeTakenMillis;
    }

    public void setTimeTakenMillis(long timeTakenMillis) {
        this.timeTakenMillis = timeTakenMillis;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    @Nullable
    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(@Nullable String errorMsg) {
        this.errorMsg = errorMsg;
    }
}

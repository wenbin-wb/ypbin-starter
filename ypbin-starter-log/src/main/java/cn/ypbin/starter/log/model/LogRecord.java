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

/**
 * 操作日志记录。
 *
 * <p>一次请求采集到的完整信息，交由 {@code LogDao} 持久化。字段按 {@code Include}
 * 采集配置选择性填充，未采集项为 {@code null}。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
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
    private Map<String, String> requestHeaders;

    /** 请求参数 */
    private String requestParam;

    /** 请求体 */
    private String requestBody;

    /** 响应头 */
    private Map<String, String> responseHeaders;

    /** 响应体 */
    private String responseBody;

    /** HTTP 状态码 */
    private Integer statusCode;

    /** 客户端 IP */
    private String ip;

    /** IP 归属地 */
    private String location;

    /** 浏览器 */
    private String browser;

    /** 操作系统 */
    private String os;

    /** 操作人 ID */
    private Long userId;

    /** 请求开始时间 */
    private Instant timestamp;

    /** 耗时（毫秒） */
    private long timeTakenMillis;

    /** 是否成功 */
    private boolean success;

    /** 错误信息 */
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

    public Map<String, String> getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(Map<String, String> requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    public String getRequestParam() {
        return requestParam;
    }

    public void setRequestParam(String requestParam) {
        this.requestParam = requestParam;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public Map<String, String> getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(Map<String, String> responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
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

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
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

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }
}

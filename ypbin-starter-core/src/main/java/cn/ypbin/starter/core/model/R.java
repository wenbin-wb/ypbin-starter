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
package cn.ypbin.starter.core.model;

import cn.ypbin.starter.core.exception.ErrorCode;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 统一响应体。
 *
 * <p>所有对外接口的返回结构，包含状态码、提示信息、数据载荷、成功标识与时间戳。
 * 通过静态工厂方法构造，避免直接 new 带来的字段遗漏。</p>
 *
 * @param <T> 数据载荷类型
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class R<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务状态码 */
    private int code;

    /** 提示信息 */
    private String message;

    /** 数据载荷 */
    private T data;

    /** 是否成功 */
    private boolean success;

    /** 响应时间戳 */
    private LocalDateTime timestamp;

    public R() {
        this.timestamp = LocalDateTime.now();
    }

    private R(int code, String message, T data, boolean success) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.success = success;
        this.timestamp = LocalDateTime.now();
    }

    // ------------------------------------------------------------------ 成功

    public static <T> R<T> ok() {
        return new R<>(GlobalErrorCode.SUCCESS.getCode(), GlobalErrorCode.SUCCESS.getMessage(), null, true);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(GlobalErrorCode.SUCCESS.getCode(), GlobalErrorCode.SUCCESS.getMessage(), data, true);
    }

    public static <T> R<T> ok(String message, T data) {
        return new R<>(GlobalErrorCode.SUCCESS.getCode(), message, data, true);
    }

    // ------------------------------------------------------------------ 失败

    public static <T> R<T> fail() {
        return new R<>(GlobalErrorCode.INTERNAL_ERROR.getCode(), GlobalErrorCode.INTERNAL_ERROR.getMessage(), null, false);
    }

    public static <T> R<T> fail(String message) {
        return new R<>(GlobalErrorCode.INTERNAL_ERROR.getCode(), message, null, false);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null, false);
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return new R<>(errorCode.getCode(), errorCode.getMessage(), null, false);
    }

    public static <T> R<T> fail(ErrorCode errorCode, String message) {
        return new R<>(errorCode.getCode(), message, null, false);
    }

    // ------------------------------------------------------------------ getter / setter

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}

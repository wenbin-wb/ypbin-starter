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
package cn.ypbin.starter.core.exception;

/**
 * 框架内置全局错误码。
 *
 * <p>沿用类 HTTP 语义的三位码，业务自定义错误码建议使用其它区段（如四位以上）以示区分。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public enum GlobalErrorCode implements ErrorCode {

    /** 成功 */
    SUCCESS(200, "操作成功"),

    /** 请求参数错误 */
    BAD_REQUEST(400, "请求参数有误"),

    /** 未认证 */
    UNAUTHORIZED(401, "登录状态已过期，请重新登录"),

    /** 无权限 */
    FORBIDDEN(403, "没有访问权限"),

    /** 资源不存在 */
    NOT_FOUND(404, "请求的资源不存在"),

    /** 业务校验失败 */
    BUSINESS_ERROR(409, "业务处理失败"),

    /** 系统内部错误 */
    INTERNAL_ERROR(500, "系统内部错误，请稍后重试");

    private final int code;
    private final String message;

    GlobalErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}

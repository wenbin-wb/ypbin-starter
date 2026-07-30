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

import java.io.Serial;

/**
 * 框架异常基类。
 *
 * <p>所有自定义异常的根，携带 {@link ErrorCode} 以便全局异常处理器统一转换为响应体。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class BaseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 错误码 */
    private final int code;

    public BaseException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BaseException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public BaseException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BaseException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.code = errorCode.getCode();
    }

    public int getCode() {
        return code;
    }
}

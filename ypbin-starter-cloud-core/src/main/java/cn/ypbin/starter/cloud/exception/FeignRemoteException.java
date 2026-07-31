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
package cn.ypbin.starter.cloud.exception;

import cn.ypbin.starter.core.exception.BaseException;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import java.io.Serial;

/**
 * Feign 远程调用异常。
 *
 * <p>用于承载下游服务返回的统一错误码，或无法解析时的 HTTP 状态码，交由上游全局异常处理器
 * 转换为统一 {@code R} 响应。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class FeignRemoteException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int status;

    private final String methodKey;

    public FeignRemoteException(int code, String message, int status, String methodKey) {
        super(code, message);
        this.status = status;
        this.methodKey = methodKey;
    }

    public FeignRemoteException(String message, int status, String methodKey) {
        this(GlobalErrorCode.INTERNAL_ERROR.getCode(), message, status, methodKey);
    }

    public int getStatus() {
        return status;
    }

    public String getMethodKey() {
        return methodKey;
    }
}

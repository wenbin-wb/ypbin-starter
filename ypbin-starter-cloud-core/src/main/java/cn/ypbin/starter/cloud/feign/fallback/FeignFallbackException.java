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
package cn.ypbin.starter.cloud.feign.fallback;

import java.io.Serial;

/**
 * Feign fallback 自定义失败异常。
 *
 * <p>业务 fallback 可抛出或传入本异常，以指定更准确的业务错误码与提示。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class FeignFallbackException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;

    public FeignFallbackException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}

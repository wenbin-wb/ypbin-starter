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
package cn.ypbin.starter.tools.idempotent;

import cn.ypbin.starter.core.exception.BaseException;
import java.io.Serial;

/**
 * 幂等冲突异常（命中重复提交）。
 *
 * <p>使用 HTTP 409 语义的业务码，交由全局异常处理器统一转换为响应体。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class IdempotentException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public IdempotentException(String message) {
        super(409, message);
    }
}

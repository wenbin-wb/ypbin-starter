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
package cn.ypbin.starter.security.client;

import cn.ypbin.starter.core.exception.BusinessException;
import java.io.Serial;

/**
 * 登录客户端校验异常。
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class LoginClientException extends BusinessException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LoginClientException(String message) {
        super(message);
    }
}

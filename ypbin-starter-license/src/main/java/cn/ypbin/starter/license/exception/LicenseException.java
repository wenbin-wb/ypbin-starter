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
package cn.ypbin.starter.license.exception;

import cn.ypbin.starter.core.exception.BaseException;
import java.io.Serial;

/**
 * License 授权异常。
 *
 * <p>授权校验链路（加载、解密、验签、指纹匹配、期限、联机、额度）任一环节失败时抛出，
 * 携带 {@link LicenseErrorCode} 供全局异常处理器转为统一响应体。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
public class LicenseException extends BaseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LicenseException(LicenseErrorCode errorCode) {
        super(errorCode);
    }

    public LicenseException(LicenseErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public LicenseException(LicenseErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}

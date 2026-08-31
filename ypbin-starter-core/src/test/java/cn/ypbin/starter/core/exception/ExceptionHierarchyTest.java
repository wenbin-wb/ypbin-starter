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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 异常体系测试：错误码、消息与 cause 的传递契约。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class ExceptionHierarchyTest {

    @Test
    void baseExceptionShouldCarryCodeAndMessage() {
        BaseException e = new BaseException(GlobalErrorCode.NOT_FOUND);
        assertThat(e.getCode()).isEqualTo(GlobalErrorCode.NOT_FOUND.getCode());
        assertThat(e.getMessage()).isEqualTo(GlobalErrorCode.NOT_FOUND.getMessage());
    }

    @Test
    void baseExceptionShouldAllowCustomMessage() {
        BaseException e = new BaseException(GlobalErrorCode.FORBIDDEN, "自定义提示");
        assertThat(e.getCode()).isEqualTo(GlobalErrorCode.FORBIDDEN.getCode());
        assertThat(e.getMessage()).isEqualTo("自定义提示");
    }

    @Test
    void baseExceptionShouldCarryCause() {
        IllegalStateException cause = new IllegalStateException("底层原因");
        BaseException e = new BaseException(GlobalErrorCode.INTERNAL_ERROR, cause);
        assertThat(e.getCause()).isSameAs(cause);
        assertThat(e.getMessage()).isEqualTo(GlobalErrorCode.INTERNAL_ERROR.getMessage());
    }

    @Test
    void businessExceptionDefaultsToBusinessError() {
        BusinessException e = new BusinessException("业务失败");
        assertThat(e.getCode()).isEqualTo(GlobalErrorCode.BUSINESS_ERROR.getCode());
        assertThat(e.getMessage()).isEqualTo("业务失败");
    }

    @Test
    void businessExceptionAcceptsCustomCode() {
        BusinessException e = new BusinessException(10001, "自定义错误码");
        assertThat(e.getCode()).isEqualTo(10001);
        assertThat(e.getMessage()).isEqualTo("自定义错误码");
    }

    @Test
    void globalErrorCodeShouldExposeCodeAndMessage() {
        assertThat(GlobalErrorCode.SUCCESS.getCode()).isEqualTo(200);
        assertThat(GlobalErrorCode.BAD_REQUEST.getCode()).isEqualTo(400);
        assertThat(GlobalErrorCode.UNAUTHORIZED.getCode()).isEqualTo(401);
        assertThat(GlobalErrorCode.FORBIDDEN.getCode()).isEqualTo(403);
        assertThat(GlobalErrorCode.NOT_FOUND.getCode()).isEqualTo(404);
        assertThat(GlobalErrorCode.METHOD_NOT_ALLOWED.getCode()).isEqualTo(405);
        assertThat(GlobalErrorCode.BUSINESS_ERROR.getCode()).isEqualTo(409);
        assertThat(GlobalErrorCode.TOO_MANY_REQUESTS.getCode()).isEqualTo(429);
        assertThat(GlobalErrorCode.INTERNAL_ERROR.getCode()).isEqualTo(500);
        // 所有错误码消息非空且 code 唯一
        assertThat(GlobalErrorCode.values())
            .allSatisfy(c -> assertThat(c.getMessage()).isNotBlank());
        assertThat(GlobalErrorCode.values())
            .extracting(GlobalErrorCode::getCode)
            .doesNotHaveDuplicates();
    }

    @Test
    void errorCodeShouldBeSerializableThroughInterface() {
        ErrorCode code = GlobalErrorCode.NOT_FOUND;
        assertThat(code.getCode()).isEqualTo(404);
        assertThat(code.getMessage()).isNotBlank();
    }
}

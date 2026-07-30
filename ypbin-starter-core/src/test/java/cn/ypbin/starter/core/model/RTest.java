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

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.core.exception.GlobalErrorCode;
import org.junit.jupiter.api.Test;

/**
 * {@link R} 统一响应体单元测试。
 *
 * @author wenbin
 * @since 2026-07-30
 */
class RTest {

    @Test
    void ok_withData_isSuccess() {
        R<String> r = R.ok("hello");
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getCode()).isEqualTo(GlobalErrorCode.SUCCESS.getCode());
        assertThat(r.getData()).isEqualTo("hello");
        assertThat(r.getTimestamp()).isNotNull();
    }

    @Test
    void ok_noArg_hasNullData() {
        R<Void> r = R.ok();
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getData()).isNull();
    }

    @Test
    void fail_withCodeAndMessage() {
        R<Void> r = R.fail(500, "服务器错误");
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getCode()).isEqualTo(500);
        assertThat(r.getMessage()).isEqualTo("服务器错误");
    }

    @Test
    void fail_withErrorCode() {
        R<Void> r = R.fail(GlobalErrorCode.NOT_FOUND);
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getCode()).isEqualTo(GlobalErrorCode.NOT_FOUND.getCode());
        assertThat(r.getMessage()).isEqualTo(GlobalErrorCode.NOT_FOUND.getMessage());
    }
}

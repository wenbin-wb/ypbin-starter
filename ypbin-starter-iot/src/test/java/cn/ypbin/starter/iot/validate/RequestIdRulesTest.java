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
package cn.ypbin.starter.iot.validate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 请求 ID 形态口径用例（下沉口径，行为与 ypbin-iot 原实现一致）。
 *
 * @author wenbin
 * @since 3.8.0
 */
class RequestIdRulesTest {

    @Test
    @DisplayName("合法形态放行（含 _:.- 边界字符）")
    void validShapesMustPass() {
        assertThat(RequestIdRules.isValid("cmd-1")).isTrue();
        assertThat(RequestIdRules.isValid("a:b.c_d-e")).isTrue();
        assertThat(RequestIdRules.isValid("x".repeat(RequestIdRules.MAX_LENGTH))).isTrue();
    }

    @Test
    @DisplayName("null/空/超长/非法字符一律拒绝")
    void invalidShapesMustFail() {
        assertThat(RequestIdRules.isValid(null)).isFalse();
        assertThat(RequestIdRules.isValid("")).isFalse();
        assertThat(RequestIdRules.isValid("   ")).isFalse();
        assertThat(RequestIdRules.isValid("x".repeat(RequestIdRules.MAX_LENGTH + 1))).isFalse();
        assertThat(RequestIdRules.isValid("a/b")).isFalse();
        assertThat(RequestIdRules.isValid("a@b")).isFalse();
        assertThat(RequestIdRules.isValid("a b")).isFalse();
    }
}

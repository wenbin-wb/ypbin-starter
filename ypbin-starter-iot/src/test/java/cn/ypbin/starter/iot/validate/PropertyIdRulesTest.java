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
 * 点位标识口径用例（下沉口径，行为与 ypbin-iot 原实现一致）。
 *
 * @author wenbin
 * @since 3.8.0
 */
class PropertyIdRulesTest {

    @Test
    @DisplayName("合法点位放行（含 _:.- 边界字符与 128 上限）")
    void validIdsMustPass() {
        assertThat(PropertyIdRules.isValid("temperature")).isTrue();
        assertThat(PropertyIdRules.isValid("a:b.c_d-e")).isTrue();
        assertThat(PropertyIdRules.isValid("x".repeat(PropertyIdRules.MAX_LENGTH))).isTrue();
    }

    @Test
    @DisplayName("null/空/超长/非法字符一律拒绝")
    void invalidIdsMustFail() {
        assertThat(PropertyIdRules.isValid(null)).isFalse();
        assertThat(PropertyIdRules.isValid("")).isFalse();
        assertThat(PropertyIdRules.isValid("x".repeat(PropertyIdRules.MAX_LENGTH + 1))).isFalse();
        assertThat(PropertyIdRules.isValid("a/b")).isFalse();
        assertThat(PropertyIdRules.isValid("select *")).isFalse();
    }

    @Test
    @DisplayName("非法原因文案与上限联动（改上限不忘改文案）")
    void invalidMessageMustTrackMaxLength() {
        assertThat(PropertyIdRules.INVALID_MESSAGE).contains(String.valueOf(PropertyIdRules.MAX_LENGTH));
    }
}

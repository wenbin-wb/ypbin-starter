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
package cn.ypbin.starter.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link RequestIdUtils} 单元测试：合法值保留、非法值拒绝并兜底生成。
 *
 * @author wenbin
 * @since 2026-09-13
 */
class RequestIdUtilsTest {

    @Test
    void generateShouldReturn32CharHexWithoutDash() {
        String id = RequestIdUtils.generate();
        assertThat(id).hasSize(32).doesNotContain("-");
        assertThat(id).isNotEqualTo(RequestIdUtils.generate());
    }

    @Test
    void sanitizeShouldKeepLegalValue() {
        assertThat(RequestIdUtils.sanitize("abc-123_X.Y")).isEqualTo("abc-123_X.Y");
        assertThat(RequestIdUtils.sanitize("  abc  ")).isEqualTo("abc");
    }

    @Test
    void sanitizeShouldRejectNullOrBlank() {
        assertThat(RequestIdUtils.sanitize(null)).isNull();
        assertThat(RequestIdUtils.sanitize("")).isNull();
        assertThat(RequestIdUtils.sanitize("   ")).isNull();
    }

    @Test
    void sanitizeShouldRejectOverlongValue() {
        assertThat(RequestIdUtils.sanitize("a".repeat(129))).isNull();
        assertThat(RequestIdUtils.sanitize("a".repeat(128))).hasSize(128);
    }

    @Test
    void sanitizeShouldRejectLogInjectionCharacters() {
        // CRLF 与制表符可用于伪造日志行，必须拒绝
        assertThat(RequestIdUtils.sanitize("abc\r\nfake-log-line")).isNull();
        assertThat(RequestIdUtils.sanitize("abc\tdef")).isNull();
        assertThat(RequestIdUtils.sanitize("abc\u001b[31mred")).isNull();
        assertThat(RequestIdUtils.sanitize("中文链路")).isNull();
    }

    @Test
    void sanitizeOrGenerateShouldFallbackToNewId() {
        assertThat(RequestIdUtils.sanitizeOrGenerate("ok-1")).isEqualTo("ok-1");
        assertThat(RequestIdUtils.sanitizeOrGenerate("bad\r\nvalue")).hasSize(32);
        assertThat(RequestIdUtils.sanitizeOrGenerate(null)).hasSize(32);
    }
}

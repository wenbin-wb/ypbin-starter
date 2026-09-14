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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link LogSanitizer} 单元测试。
 *
 * @author wenbin
 * @since 2026-09-14
 */
class LogSanitizerTest {

    @Test
    @DisplayName("换行/制表/控制字符替换为下划线，杜绝跨行伪造")
    void shouldStripLineBreaksAndControlChars() {
        assertThat(LogSanitizer.sanitize("ok\r\nFAKE [登录成功]")).isEqualTo("ok__FAKE [登录成功]");
        assertThat(LogSanitizer.sanitize("a\nb\tc")).isEqualTo("a_b_c");
        assertThat(LogSanitizer.sanitize("x\u0000y\u001bz")).isEqualTo("x_y_z");
        assertThat(LogSanitizer.sanitize("del\u007f")).isEqualTo("del_");
    }

    @Test
    @DisplayName("正常文本原样保留")
    void shouldKeepNormalText() {
        assertThat(LogSanitizer.sanitize("/api/user?id=1")).isEqualTo("/api/user?id=1");
        assertThat(LogSanitizer.sanitize("中文与 emoji 🚀")).isEqualTo("中文与 emoji 🚀");
    }

    @Test
    @DisplayName("null 与非字符串入参都有确定输出")
    void shouldHandleNullAndNonString() {
        assertThat(LogSanitizer.sanitize(null)).isEqualTo("null");
        assertThat(LogSanitizer.sanitize(42)).isEqualTo("42");
        assertThat(LogSanitizer.sanitize(new Object() {
            @Override
            public String toString() {
                return "custom\ntoString";
            }
        })).isEqualTo("custom_toString");
    }

    @Test
    @DisplayName("超长字段截断，避免日志膨胀")
    void shouldTruncateLongValue() {
        String longValue = "a".repeat(600);
        String sanitized = LogSanitizer.sanitize(longValue);
        assertThat(sanitized).hasSize(500 + "...(truncated)".length()).endsWith("...(truncated)");
        // 恰好等于上限时不截断
        assertThat(LogSanitizer.sanitize("b".repeat(500))).hasSize(500);
    }
}

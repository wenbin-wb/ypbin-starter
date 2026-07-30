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
package cn.ypbin.starter.json.sensitive;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link SensitiveType} 脱敏策略单元测试。
 *
 * @author wenbin
 * @since 2026-07-30
 */
class SensitiveTypeTest {

    @Test
    void phone_keepPrefix3Suffix4() {
        assertThat(SensitiveType.PHONE.apply("13800008000", 0, 0)).isEqualTo("138****8000");
    }

    @Test
    void chineseName_keepFirst() {
        assertThat(SensitiveType.CHINESE_NAME.apply("张三丰", 0, 0)).isEqualTo("张**");
    }

    @Test
    void email_maskLocalPart() {
        assertThat(SensitiveType.EMAIL.apply("admin@example.com", 0, 0)).isEqualTo("a***@example.com");
    }

    @Test
    void bankCard_keepLast4() {
        assertThat(SensitiveType.BANK_CARD.apply("6222021234567890", 0, 0)).endsWith("7890");
        assertThat(SensitiveType.BANK_CARD.apply("6222021234567890", 0, 0)).startsWith("*");
    }

    @Test
    void custom_keepPrefixAndSuffix() {
        assertThat(SensitiveType.CUSTOM.apply("abcdefgh", 2, 2)).isEqualTo("ab****gh");
    }

    @Test
    void whenKeepExceedsLength_maskAll() {
        // 保留位数超过总长，应全部打码，避免泄露
        assertThat(SensitiveType.CUSTOM.apply("ab", 3, 3)).isEqualTo("**");
    }

    @Test
    void nullOrEmpty_returnedAsIs() {
        assertThat(SensitiveType.PHONE.apply(null, 0, 0)).isNull();
        assertThat(SensitiveType.PHONE.apply("", 0, 0)).isEmpty();
    }
}

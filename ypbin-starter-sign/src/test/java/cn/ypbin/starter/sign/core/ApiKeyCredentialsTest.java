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
package cn.ypbin.starter.sign.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.core.exception.BusinessException;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * API Key 凭证原语用例（与 ypbin-iot 开放 API Key 的过渡实现同语义，切换后以本测试为准）。
 *
 * @author wenbin
 * @since 2026-10-02
 */
class ApiKeyCredentialsTest {

    private static final String PEPPER = "test-pepper-not-a-real-secret";

    @Test
    @DisplayName("生成：长度与字符集正确 + 每次不同")
    void generateMustBeUniqueAndUrlSafe() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            String secret = ApiKeyCredentials.generateSecret(ApiKeyCredentials.SECRET_BYTE_LENGTH);
            assertThat(secret).matches("[A-Za-z0-9_-]+");
            seen.add(secret);
        }
        assertThat(seen).hasSize(10);
    }

    @Test
    @DisplayName("生成：非正数字节数拒绝")
    void generateWithNonPositiveLengthMustFail() {
        assertThatThrownBy(() -> ApiKeyCredentials.generateSecret(0))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("哈希：同输入同输出，换 pepper 即变")
    void hashMustBeDeterministicPerPepper() {
        String first = ApiKeyCredentials.hashSecret(PEPPER, "sk_demo");
        assertThat(first).hasSize(64);
        assertThat(ApiKeyCredentials.hashSecret(PEPPER, "sk_demo")).isEqualTo(first);
        assertThat(ApiKeyCredentials.hashSecret("other-pepper", "sk_demo")).isNotEqualTo(first);
    }

    @Test
    @DisplayName("哈希：缺 pepper/明文直接拒绝（fail-closed）")
    void hashWithoutPepperMustFail() {
        assertThatThrownBy(() -> ApiKeyCredentials.hashSecret("", "sk_demo"))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ApiKeyCredentials.hashSecret(PEPPER, ""))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("比对：正确放行，错误/缺失统一 false（不抛异常，防枚举）")
    void matchesMustFailClosedWithoutDistinction() {
        String hash = ApiKeyCredentials.hashSecret(PEPPER, "sk_demo");
        assertThat(ApiKeyCredentials.matches(PEPPER, "sk_demo", hash)).isTrue();
        assertThat(ApiKeyCredentials.matches(PEPPER, "  sk_demo  ", hash)).isTrue();
        assertThat(ApiKeyCredentials.matches(PEPPER, "sk_wrong", hash)).isFalse();
        assertThat(ApiKeyCredentials.matches("", "sk_demo", hash)).isFalse();
        assertThat(ApiKeyCredentials.matches(PEPPER, "", hash)).isFalse();
        assertThat(ApiKeyCredentials.matches(PEPPER, "sk_demo", "")).isFalse();
    }

    @Test
    @DisplayName("回显：前缀 + 可见字符，不足时全显不明文外泄")
    void displayPrefixMustShowOnlyHead() {
        assertThat(ApiKeyCredentials.displayPrefix("sk_abcdefgh", "sk_", 4)).isEqualTo("sk_abcd");
        assertThat(ApiKeyCredentials.displayPrefix("sk_ab", "sk_", 8)).isEqualTo("sk_ab");
    }
}

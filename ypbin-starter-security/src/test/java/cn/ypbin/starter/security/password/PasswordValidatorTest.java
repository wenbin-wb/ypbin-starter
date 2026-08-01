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
package cn.ypbin.starter.security.password;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.security.password.policy.PasswordCheckResult;
import cn.ypbin.starter.security.password.policy.PasswordPolicy;
import cn.ypbin.starter.security.password.policy.PasswordValidator;
import org.junit.jupiter.api.Test;

/**
 * {@link PasswordValidator} 复杂度校验测试。
 *
 * @author wenbin
 * @since 2026-08-01
 */
class PasswordValidatorTest {

    private PasswordValidator validator(PasswordPolicy policy) {
        return new PasswordValidator(() -> policy);
    }

    @Test
    void shouldPassDefaultPolicy() {
        PasswordCheckResult result = validator(new PasswordPolicy()).check("abc12345");
        assertThat(result.passed()).isTrue();
    }

    @Test
    void shouldRejectTooShort() {
        PasswordCheckResult result = validator(new PasswordPolicy()).check("ab12");
        assertThat(result.passed()).isFalse();
        assertThat(result.message()).contains("长度");
    }

    @Test
    void shouldRejectMissingDigit() {
        PasswordCheckResult result = validator(new PasswordPolicy()).check("abcdefgh");
        assertThat(result.passed()).isFalse();
        assertThat(result.message()).contains("数字");
    }

    @Test
    void shouldRejectMissingSymbolWhenRequired() {
        PasswordPolicy policy = new PasswordPolicy();
        policy.setRequireSymbol(true);
        PasswordCheckResult result = validator(policy).check("abc12345");
        assertThat(result.passed()).isFalse();
        assertThat(result.message()).contains("特殊字符");
    }

    @Test
    void shouldPassWithSymbolWhenRequired() {
        PasswordPolicy policy = new PasswordPolicy();
        policy.setRequireSymbol(true);
        assertThat(validator(policy).check("abc12345!").passed()).isTrue();
    }

    @Test
    void shouldRejectContainingUsername() {
        PasswordCheckResult result = validator(new PasswordPolicy()).check("tom12345", "tom");
        assertThat(result.passed()).isFalse();
        assertThat(result.message()).contains("用户名");
    }

    @Test
    void shouldRejectReversedUsername() {
        PasswordCheckResult result = validator(new PasswordPolicy()).check("mot12345", "tom");
        assertThat(result.passed()).isFalse();
    }

    @Test
    void shouldAllowUsernameWhenPolicyAllows() {
        PasswordPolicy policy = new PasswordPolicy();
        policy.setAllowContainUsername(true);
        assertThat(validator(policy).check("tom12345", "tom").passed()).isTrue();
    }
}

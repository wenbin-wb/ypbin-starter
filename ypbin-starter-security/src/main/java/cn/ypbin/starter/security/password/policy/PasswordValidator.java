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
package cn.ypbin.starter.security.password.policy;

import java.util.Locale;

/**
 * 密码复杂度校验器。
 *
 * <p>按 {@link PasswordPolicyProvider} 提供的策略校验明文密码是否合规：长度、数字、字母、大小写、
 * 特殊字符、是否包含用户名等。策略来自 provider，运行时可动态调整。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class PasswordValidator {

    /** 特殊字符集 */
    private static final String SYMBOLS = "~!@#$%^&*()_+-=[]{}|;:',.<>/?";

    private final PasswordPolicyProvider policyProvider;

    public PasswordValidator(PasswordPolicyProvider policyProvider) {
        this.policyProvider = policyProvider;
    }

    /**
     * 校验密码复杂度。
     *
     * @param rawPassword 明文密码
     * @return 校验结果
     */
    public PasswordCheckResult check(String rawPassword) {
        return check(rawPassword, null);
    }

    /**
     * 校验密码复杂度（结合用户名判断是否包含用户名）。
     *
     * @param rawPassword 明文密码
     * @param username    用户名，可空
     * @return 校验结果
     */
    public PasswordCheckResult check(String rawPassword, String username) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            return PasswordCheckResult.fail("密码不能为空");
        }
        PasswordPolicy policy = policyProvider.getPolicy();

        int length = rawPassword.length();
        if (length < policy.getMinLength()) {
            return PasswordCheckResult.fail("密码长度不能少于 " + policy.getMinLength() + " 位");
        }
        if (length > policy.getMaxLength()) {
            return PasswordCheckResult.fail("密码长度不能超过 " + policy.getMaxLength() + " 位");
        }
        if (policy.isRequireDigit() && !contains(rawPassword, Character::isDigit)) {
            return PasswordCheckResult.fail("密码必须包含数字");
        }
        if (policy.isRequireUppercase() && !contains(rawPassword, Character::isUpperCase)) {
            return PasswordCheckResult.fail("密码必须包含大写字母");
        }
        if (policy.isRequireLowercase() && !contains(rawPassword, Character::isLowerCase)) {
            return PasswordCheckResult.fail("密码必须包含小写字母");
        }
        // 已要求大写或小写时，"必须含字母"已被覆盖，无需重复校验
        boolean letterCoveredByCase = policy.isRequireUppercase() || policy.isRequireLowercase();
        if (policy.isRequireLetter() && !letterCoveredByCase && !contains(rawPassword, Character::isLetter)) {
            return PasswordCheckResult.fail("密码必须包含字母");
        }
        if (policy.isRequireSymbol() && !contains(rawPassword, c -> SYMBOLS.indexOf(c) >= 0)) {
            return PasswordCheckResult.fail("密码必须包含特殊字符");
        }
        if (!policy.isAllowContainUsername() && containsUsername(rawPassword, username)) {
            return PasswordCheckResult.fail("密码不能包含用户名");
        }
        return PasswordCheckResult.pass();
    }

    private boolean contains(String value, CharPredicate predicate) {
        for (int i = 0; i < value.length(); i++) {
            if (predicate.test(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsUsername(String rawPassword, String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        String lowerPwd = rawPassword.toLowerCase(Locale.ROOT);
        String lowerName = username.toLowerCase(Locale.ROOT);
        String reversedName = new StringBuilder(lowerName).reverse().toString();
        return lowerPwd.contains(lowerName) || lowerPwd.contains(reversedName);
    }

    @FunctionalInterface
    private interface CharPredicate {
        boolean test(char c);
    }
}

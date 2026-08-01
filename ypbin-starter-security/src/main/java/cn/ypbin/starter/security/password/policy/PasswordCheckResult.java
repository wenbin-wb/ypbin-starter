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

/**
 * 密码复杂度校验结果。
 *
 * @param passed  是否通过
 * @param message 不通过原因（通过时为空）
 * @author wenbin
 * @since 2026-08-01
 */
public record PasswordCheckResult(boolean passed, String message) {

    /**
     * 通过结果。
     *
     * @return 通过
     */
    public static PasswordCheckResult pass() {
        return new PasswordCheckResult(true, "");
    }

    /**
     * 不通过结果。
     *
     * @param message 原因
     * @return 不通过
     */
    public static PasswordCheckResult fail(String message) {
        return new PasswordCheckResult(false, message);
    }
}

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

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码工具。
 *
 * <p>基于 Spring Security Crypto 的 BCrypt。加密自带随机盐，同一明文每次密文不同；
 * 校验用 {@link #matches} 而非比较密文。仅依赖 spring-security-crypto，不引入完整 Spring Security。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public final class PasswordEncoderUtil {

    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private PasswordEncoderUtil() {
    }

    /**
     * 加密明文密码。
     *
     * @param rawPassword 明文
     * @return BCrypt 密文（含盐）
     */
    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /**
     * 校验明文与密文是否匹配。
     *
     * @param rawPassword     明文
     * @param encodedPassword 密文
     * @return 是否匹配
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return ENCODER.matches(rawPassword, encodedPassword);
    }

    /**
     * 获取底层编码器（供注册为 Spring Bean 等场景）。
     *
     * @return {@link PasswordEncoder}
     */
    public static PasswordEncoder getEncoder() {
        return ENCODER;
    }
}

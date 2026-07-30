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

import java.util.function.Function;

/**
 * 脱敏类型。
 *
 * <p>每种类型内置一个脱敏策略函数。序列化时对原值应用策略得到脱敏结果，不改动原始数据。
 * 特殊类型 {@link #CUSTOM} 表示由注解上的自定义前后保留位数决定。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public enum SensitiveType {

    /** 中文姓名：保留姓，名用 * 代替（张三 -> 张*） */
    CHINESE_NAME(v -> mask(v, 1, 0)),

    /** 手机号：保留前 3 后 4（138****8000） */
    PHONE(v -> mask(v, 3, 4)),

    /** 身份证：保留前 6 后 4 */
    ID_CARD(v -> mask(v, 6, 4)),

    /** 邮箱：保留首字符与 @ 后完整域名（a***@example.com） */
    EMAIL(SensitiveType::maskEmail),

    /** 银行卡：保留后 4 位 */
    BANK_CARD(v -> mask(v, 0, 4)),

    /** 地址：保留前 6 位，其余打码 */
    ADDRESS(v -> mask(v, 6, 0)),

    /** 全部打码 */
    ALL(v -> v == null ? null : "*".repeat(v.length())),

    /** 自定义：按注解的 prefixKeep / suffixKeep 保留位数 */
    CUSTOM(null);

    private final Function<String, String> strategy;

    SensitiveType(Function<String, String> strategy) {
        this.strategy = strategy;
    }

    /**
     * 应用脱敏。
     *
     * @param value       原值
     * @param prefixKeep  自定义类型时保留的前缀位数
     * @param suffixKeep  自定义类型时保留的后缀位数
     * @return 脱敏后的值
     */
    public String apply(String value, int prefixKeep, int suffixKeep) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (this == CUSTOM) {
            return mask(value, prefixKeep, suffixKeep);
        }
        return strategy.apply(value);
    }

    /**
     * 通用脱敏：保留前 prefixKeep、后 suffixKeep 位，中间用 * 填充。
     */
    private static String mask(String value, int prefixKeep, int suffixKeep) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int len = value.length();
        if (prefixKeep + suffixKeep >= len) {
            // 保留位数超过总长，全部打码，避免泄露
            return "*".repeat(len);
        }
        String prefix = value.substring(0, prefixKeep);
        String suffix = value.substring(len - suffixKeep);
        int maskLen = len - prefixKeep - suffixKeep;
        return prefix + "*".repeat(maskLen) + suffix;
    }

    private static String maskEmail(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int at = value.indexOf('@');
        if (at <= 0) {
            return mask(value, 1, 0);
        }
        String name = value.substring(0, at);
        String domain = value.substring(at);
        String maskedName = name.length() <= 1 ? name : name.charAt(0) + "***";
        return maskedName + domain;
    }
}

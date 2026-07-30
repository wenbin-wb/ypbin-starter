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

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段脱敏注解。
 *
 * <p>标注在 String 字段上，序列化为 JSON 时按 {@link #value() 脱敏类型} 自动打码，
 * 不影响数据库中的原始值。用于响应体中的手机号、身份证、银行卡等敏感字段。</p>
 *
 * <pre>{@code
 * @Sensitive(SensitiveType.PHONE)
 * private String phone;         // 输出 138****8000
 *
 * @Sensitive(value = SensitiveType.CUSTOM, prefixKeep = 2, suffixKeep = 2)
 * private String custom;
 * }</pre>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = SensitiveSerializer.class)
public @interface Sensitive {

    /**
     * 脱敏类型。
     *
     * @return 类型
     */
    SensitiveType value();

    /**
     * 自定义类型时保留的前缀位数。
     *
     * @return 前缀保留位数
     */
    int prefixKeep() default 0;

    /**
     * 自定义类型时保留的后缀位数。
     *
     * @return 后缀保留位数
     */
    int suffixKeep() default 0;
}

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
package cn.ypbin.starter.json.dict;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字典文本翻译注解。
 *
 * <p>标注在存字典值（code）的字段上，序列化为 JSON 时：<b>保留原字段原值不变</b>，并<b>额外输出</b>一个
 * 展示文本字段（默认名 {@code 原字段名 + "Text"}）。遵循全链路字段同名约定——不改原字段名，只增派生展示字段。</p>
 *
 * <pre>{@code
 * @DictText("sys_user_status")
 * private String status;          // 输出 "status":"1","statusText":"正常"
 *
 * @DictText(value = "gender", suffix = "Label")
 * private String gender;          // 输出 "gender":"1","genderLabel":"男"
 * }</pre>
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = DictTextSerializer.class)
public @interface DictText {

    /**
     * 字典类型编码。
     *
     * @return 字典类型
     */
    String value();

    /**
     * 派生展示字段的名称后缀，默认 {@code Text}（即输出 {@code 原字段名 + "Text"}）。
     *
     * @return 后缀
     */
    String suffix() default "Text";
}

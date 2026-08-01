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
package cn.ypbin.starter.json.ref;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 引用翻译注解。
 *
 * <p>标注在存引用 ID 的字段上（如 createUser、deptId），序列化为 JSON 时：<b>保留原字段原值不变</b>，
 * 并<b>额外输出</b>一个展示名称字段（默认名 {@code 原字段名 + "Name"}）。遵循全链路字段同名约定——不改原字段名，
 * 只增派生展示字段。</p>
 *
 * <p>翻译数据由对应 {@code type} 的 {@link RefTextProvider} 提供，经 {@link RefTextManager} 缓存；
 * 列表批量翻译时通过 {@link RefTextResolver#preload} 预加载可将回源合并为每类型一次查询。</p>
 *
 * <pre>{@code
 * @RefText("user")
 * private Long createUser;      // 输出 "createUser":"123","createUserName":"张三"
 *
 * @RefText(value = "dept", suffix = "Text")
 * private Long deptId;          // 输出 "deptId":"8","deptIdText":"研发部"
 * }</pre>
 *
 * @author wenbin
 * @since 2026-08-01
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = RefTextSerializer.class)
public @interface RefText {

    /**
     * 引用类型标识，对应 {@link RefTextProvider#type()}。
     *
     * @return 类型标识
     */
    String value();

    /**
     * 派生展示字段的名称后缀，默认 {@code Name}（即输出 {@code 原字段名 + "Name"}）。
     *
     * @return 后缀
     */
    String suffix() default "Name";
}

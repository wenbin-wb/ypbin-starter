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
package cn.ypbin.starter.sensitivewords.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 敏感词过滤注解。
 *
 * <p><b>字段级</b>：标注在 DTO / 请求对象的 {@code String} 字段上，声明该字段的值需要做敏感词替换。
 * 字段本身不触发任何逻辑，需配合方法级 {@code @SensitiveWordFilter} 或手动调用 AOP 才生效。</p>
 *
 * <p><b>方法级</b>：标注在 Service / Controller 方法上（通常是写入操作），切面
 * {@link cn.ypbin.starter.sensitivewords.aspect.SensitiveWordFilterAspect} 会在方法执行前
 * 遍历所有入参，将参数中标注了本注解的 {@code String} 字段替换为掩码。支持嵌套对象（只处理第一层）。</p>
 *
 * <p>示例：</p>
 * <pre>{@code
 * // DTO 字段声明
 * @SensitiveWordFilter
 * private String title;
 *
 * // Service 方法触发
 * @SensitiveWordFilter
 * public void createNotice(NoticeSaveReq req) { ... }
 * }</pre>
 *
 * @author wenbin
 * @since 2026-08-14
 */
@Documented
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SensitiveWordFilter {
}

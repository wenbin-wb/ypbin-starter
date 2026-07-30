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
package cn.ypbin.starter.log.annotation;

import cn.ypbin.starter.log.enums.Include;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解。
 *
 * <p>标注在接口方法或类上采集操作日志。类上标注对其下所有方法生效，方法上可覆盖或
 * 追加。通过 {@link #includes()} / {@link #excludes()} 在全局默认采集集合基础上按需
 * 增减，{@link #ignore()} 可完全忽略某方法/类。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Log {

    /**
     * 日志描述（仅用于方法上）。
     *
     * @return 描述
     */
    String value() default "";

    /**
     * 所属模块（方法或类上）。
     *
     * @return 模块名
     */
    String module() default "";

    /**
     * 在全局默认基础上追加的采集项。
     *
     * @return 追加项
     */
    Include[] includes() default {};

    /**
     * 在全局默认基础上排除的采集项。
     *
     * @return 排除项
     */
    Include[] excludes() default {};

    /**
     * 是否忽略日志记录（方法或类上）。
     *
     * @return 是否忽略
     */
    boolean ignore() default false;
}

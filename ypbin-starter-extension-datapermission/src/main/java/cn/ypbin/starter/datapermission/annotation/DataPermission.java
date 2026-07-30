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
package cn.ypbin.starter.datapermission.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限注解。
 *
 * <p>标注在方法或类上，仅被标注的方法执行期间才触发数据范围过滤。未标注的查询
 * （定时任务、登录校验、管理员全量查询等）不受数据权限影响，避免全局无差别拦截导致
 * 内部查询数据缺失。</p>
 *
 * <p>方法级注解优先于类级；类上标注则该类所有方法默认启用。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataPermission {

    /**
     * 是否忽略数据权限（用于在已启用的类中排除个别方法）。
     *
     * @return 是否忽略
     */
    boolean ignore() default false;
}

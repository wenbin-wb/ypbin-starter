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
package cn.ypbin.starter.datapermission.aspect;

import cn.ypbin.starter.datapermission.annotation.DataPermission;
import cn.ypbin.starter.datapermission.core.DataPermissionContext;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * 数据权限切面。
 *
 * <p>拦截 {@link DataPermission} 标注的方法或类，在方法执行期间激活数据权限作用域，
 * 使 MyBatis-Plus 拦截器仅对这些查询拼接数据范围 SQL。方法级 {@code ignore=true} 可在
 * 已启用的类中排除个别方法。用 Spring 注解工具查找，避免动态代理下注解丢失。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@Aspect
public class DataPermissionAspect {

    @Around("@annotation(cn.ypbin.starter.datapermission.annotation.DataPermission) "
        + "|| @within(cn.ypbin.starter.datapermission.annotation.DataPermission)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        DataPermission methodAnno = AnnotatedElementUtils.findMergedAnnotation(method, DataPermission.class);
        DataPermission classAnno = AnnotatedElementUtils
            .findMergedAnnotation(point.getTarget().getClass(), DataPermission.class);

        // 方法级 ignore 优先；方法无注解时看类级
        boolean ignore = (methodAnno != null) ? methodAnno.ignore()
            : (classAnno != null && classAnno.ignore());
        if (ignore) {
            return point.proceed();
        }

        DataPermissionContext.enter();
        try {
            return point.proceed();
        } finally {
            DataPermissionContext.exit();
        }
    }
}

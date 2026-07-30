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
package cn.ypbin.starter.tenant.aspect;

import cn.ypbin.starter.tenant.core.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * 忽略租户切面。
 *
 * <p>拦截 {@code @TenantIgnore} 标注的方法或类，在其执行期间激活忽略租户作用域，
 * 使租户行处理器临时放行。用 Spring 注解语义匹配，避免动态代理下注解丢失。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@Aspect
public class TenantIgnoreAspect {

    @Around("@annotation(cn.ypbin.starter.tenant.annotation.TenantIgnore) "
        + "|| @within(cn.ypbin.starter.tenant.annotation.TenantIgnore)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        TenantContext.enterIgnore();
        try {
            return point.proceed();
        } finally {
            TenantContext.exitIgnore();
        }
    }
}

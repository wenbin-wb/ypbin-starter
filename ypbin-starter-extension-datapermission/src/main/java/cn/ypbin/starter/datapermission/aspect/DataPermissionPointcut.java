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

import java.lang.reflect.Method;
import org.springframework.aop.support.StaticMethodMatcherPointcut;

/**
 * {@link cn.ypbin.starter.datapermission.annotation.DataPermission} 切点。
 *
 * <p>用「解析注解」而非 AspectJ 注解表达式匹配：接口类/接口方法上的注解在 JDK 代理下也能命中，
 * 避免「注解挂在接口上 → 切面不织入 → 数据权限整体失效」。</p>
 *
 * @author wenbin
 * @since 2026-11-06
 */
public class DataPermissionPointcut extends StaticMethodMatcherPointcut {

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return DataPermissionAspect.resolveAnnotation(method, targetClass) != null;
    }
}

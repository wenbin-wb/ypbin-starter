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
package cn.ypbin.starter.license.aspect;

import cn.ypbin.starter.license.annotation.LicenseCheck;
import java.lang.reflect.Method;
import org.springframework.aop.support.StaticMethodMatcherPointcut;

/**
 * {@link LicenseCheck} 切点：在实现类方法、目标类、接口方法和接口类四级查找注解。
 *
 * <p>标准 {@code @annotation}/{@code @within} 表达式只按目标方法匹配，注解写在接口上时 CGLIB
 * 代理下不会命中。这里用静态方法匹配器复用 {@link LicenseCheckAspect#resolveAnnotation(Method, Class)}
 * 的解析口径，保证切点与切面看到同一份注解。</p>
 *
 * @author wenbin
 * @since 2026-11-06
 */
public class LicenseCheckPointcut extends StaticMethodMatcherPointcut {

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return LicenseCheckAspect.resolveAnnotation(method, targetClass) != null;
    }
}

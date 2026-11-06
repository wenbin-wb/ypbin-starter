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
package cn.ypbin.starter.security.platform;

import java.lang.reflect.Method;
import org.springframework.aop.support.StaticMethodMatcherPointcut;

/**
 * {@link PlatformAccess} 切点。
 *
 * <p>用「解析注解」而非 AspectJ 注解表达式匹配：注解写在接口类或接口方法上时，
 * JDK 动态代理下也能命中，避免「注解挂在接口上 → 切面不织入 → 平台资源静默放开」。
 * 解析口径与 {@link PlatformAccessAspect#resolveAnnotation(Method, Class)} 完全一致。</p>
 *
 * @author wenbin
 * @since 2026-11-06
 */
public class PlatformAccessPointcut extends StaticMethodMatcherPointcut {

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        return PlatformAccessAspect.resolveAnnotation(method, targetClass) != null;
    }
}

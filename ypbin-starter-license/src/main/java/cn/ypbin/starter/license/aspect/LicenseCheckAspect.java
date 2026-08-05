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
import cn.ypbin.starter.license.core.LicenseContent;
import cn.ypbin.starter.license.core.LicenseManager;
import cn.ypbin.starter.license.core.MachineFingerprint;
import cn.ypbin.starter.license.extension.RemoteVerifyProvider;
import java.lang.reflect.Method;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * License 授权校验切面。
 *
 * <p>环绕拦截 {@link LicenseCheck} 标注的方法：合并类级与方法级注解（方法级优先），进入方法前
 * 依次执行基础可用性校验、可选的模块级校验、可选的联机回验，任一不通过即抛出授权异常阻断调用。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
@Aspect
public class LicenseCheckAspect {

    private final LicenseManager manager;
    private final List<RemoteVerifyProvider> remoteVerifyProviders;

    public LicenseCheckAspect(LicenseManager manager, List<RemoteVerifyProvider> remoteVerifyProviders) {
        this.manager = manager;
        this.remoteVerifyProviders = remoteVerifyProviders;
    }

    @Around("@annotation(cn.ypbin.starter.license.annotation.LicenseCheck) "
        + "|| @within(cn.ypbin.starter.license.annotation.LicenseCheck)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        // 用 Spring 的注解查找工具，跨接口/实现类、代理类/目标类，避免 JDK 动态代理下注解丢失；方法级优先于类级
        LicenseCheck check = AnnotatedElementUtils.findMergedAnnotation(method, LicenseCheck.class);
        if (check == null) {
            check = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), LicenseCheck.class);
        }
        if (check == null) {
            return point.proceed();
        }

        // 基础可用性 + 可选模块级校验
        if (check.module().isBlank()) {
            manager.assertUsable();
        } else {
            manager.assertModule(check.module());
        }

        // 可选联机回验：交由扩展点实现，未接入时为空操作，不做静默兜底
        if (check.online()) {
            remoteVerify();
        }

        return point.proceed();
    }

    /**
     * 触发联机回验：逐个回调联机校验扩展点，任一抛异常即中断放行。
     */
    private void remoteVerify() {
        if (remoteVerifyProviders.isEmpty()) {
            return;
        }
        LicenseContent content = manager.getContent();
        String fingerprint = MachineFingerprint.current();
        for (RemoteVerifyProvider provider : remoteVerifyProviders) {
            provider.verify(content, fingerprint);
        }
    }
}

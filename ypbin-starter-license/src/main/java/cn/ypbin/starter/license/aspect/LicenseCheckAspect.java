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
import cn.ypbin.starter.license.exception.LicenseErrorCode;
import cn.ypbin.starter.license.exception.LicenseException;
import cn.ypbin.starter.license.extension.RemoteVerifyProvider;
import java.lang.reflect.Method;
import java.util.List;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

/**
 * License 授权校验切面。
 *
 * <p>环绕拦截 {@link LicenseCheck} 标注的方法：合并类级与方法级注解（方法级优先），进入方法前
 * 依次执行基础可用性校验、可选的模块级校验、可选的联机回验，任一不通过即抛出授权异常阻断调用。</p>
 *
 * <p><strong>为什么不再用 {@code @Around} 表达式匹配注解</strong>：AspectJ 的
 * {@code @annotation}/{@code @within} 按目标类方法匹配，注解只写在接口方法上时 CGLIB 代理下切点
 * 完全不命中，授权校验静默失效。本类改为普通 {@link MethodInterceptor}，由
 * {@code LicenseCheckPointcut} 同时检查实现类、目标类与接口方法/接口类上的注解，再交给本类解析执行。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
@Aspect
public class LicenseCheckAspect implements MethodInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LicenseCheckAspect.class);

    private final LicenseManager manager;
    private final List<RemoteVerifyProvider> remoteVerifyProviders;

    public LicenseCheckAspect(LicenseManager manager, List<RemoteVerifyProvider> remoteVerifyProviders) {
        this.manager = manager;
        this.remoteVerifyProviders = remoteVerifyProviders;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        return checkAndProceed(invocation.getMethod(), invocation.getThis(), invocation::proceed);
    }

    /**
     * 保留 AspectJ 连接点入口，供宿主已有配置或测试直接调用；实际自动配置走 {@link #invoke(MethodInvocation)}。
     *
     * @param point 连接点
     * @return 方法返回值
     * @throws Throwable 方法或授权校验抛出的异常
     */
    public Object around(ProceedingJoinPoint point) throws Throwable {
        return checkAndProceed(((MethodSignature) point.getSignature()).getMethod(), point.getTarget(),
            point::proceed);
    }

    private Object checkAndProceed(Method method, Object target, CheckedSupplier proceed) throws Throwable {
        Class<?> targetClass = target == null ? method.getDeclaringClass() : AopUtils.getTargetClass(target);
        LicenseCheck check = resolveAnnotation(method, targetClass);
        if (check == null) {
            // 切点已匹配却解析不到注解：多为代理/接口组合异常，必须可见，不能静默当作已校验
            log.warn("[ypbin-starter] @LicenseCheck 已匹配但未解析到注解，按未授权调用放行：{}#{}",
                targetClass.getName(), method.getName());
            return proceed.get();
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

        return proceed.get();
    }

    /**
     * 按「具体实现方法 → 目标类 → 接口方法 → 接口类」顺序解析注解，覆盖注解写在接口上的常见写法。
     *
     * @param method      代理方法（JDK 代理下为接口方法）
     * @param targetClass 目标类（非代理类）
     * @return 合并后的注解；解析不到返回 null
     */
    static LicenseCheck resolveAnnotation(Method method, Class<?> targetClass) {
        Method specific = AopUtils.getMostSpecificMethod(method, targetClass);
        LicenseCheck check = AnnotatedElementUtils.findMergedAnnotation(specific, LicenseCheck.class);
        if (check != null) {
            return check;
        }
        check = AnnotatedElementUtils.findMergedAnnotation(targetClass, LicenseCheck.class);
        if (check != null) {
            return check;
        }
        for (Class<?> ifc : ClassUtils.getAllInterfacesForClassAsSet(targetClass)) {
            check = AnnotatedElementUtils.findMergedAnnotation(ifc, LicenseCheck.class);
            if (check != null) {
                return check;
            }
            try {
                Method interfaceMethod = ifc.getMethod(specific.getName(), specific.getParameterTypes());
                check = AnnotatedElementUtils.findMergedAnnotation(interfaceMethod, LicenseCheck.class);
                if (check != null) {
                    return check;
                }
            } catch (NoSuchMethodException ignored) {
                // 接口未声明同名方法：继续检查其它接口
            }
        }
        return null;
    }

    /**
     * 触发联机回验：逐个回调联机校验扩展点，任一抛异常即中断放行。
     */
    private void remoteVerify() {
        if (remoteVerifyProviders.isEmpty()) {
            return;
        }
        LicenseContent content = manager.getContent();
        if (content == null) {
            // 不静默跳过联机回验：本地校验已通过却拿不到授权内容属状态不一致，显式失败（fail-closed）
            throw new LicenseException(LicenseErrorCode.LICENSE_NOT_YET_VALID);
        }
        String fingerprint = MachineFingerprint.current();
        for (RemoteVerifyProvider provider : remoteVerifyProviders) {
            provider.verify(content, fingerprint);
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier {
        Object get() throws Throwable;
    }
}

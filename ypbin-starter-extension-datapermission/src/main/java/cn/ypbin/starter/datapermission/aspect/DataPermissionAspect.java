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
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

/**
 * 数据权限切面：在 {@link DataPermission} 标注的方法/类作用域内激活数据范围过滤。
 *
 * <p><b>为什么不用 {@code @Around("@annotation(..) || @within(..)")}</b>：AspectJ 的注解切入点
 * 按<b>被调用方法</b>匹配，注解写在接口上而调用方持有 JDK 代理时，代理方法来自接口，
 * {@code @annotation} 可匹配但 {@code @within} 只认实现类；一旦注解只挂在接口类上，
 * 切面<b>完全不织入</b>——数据权限整体失效（越权读）且无任何报错。改为自定义切点
 * （{@link DataPermissionPointcut}）+ {@link MethodInterceptor}，按「实现方法 → 目标类 → 接口方法
 * → 接口类」逐层解析注解，两种代理模式行为一致。</p>
 *
 * <p>{@code ignore=true} 同样按「最具体方法优先」判定：方法级注解压过类级，
 * 因此实现类方法上的 {@code ignore=true} 在 JDK 代理下也不会丢失。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class DataPermissionAspect implements MethodInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DataPermissionAspect.class);

    /**
     * 兼容 AspectJ 调用方式的入口（历史签名）。
     *
     * @param point 连接点
     * @return 方法返回值
     * @throws Throwable 目标方法抛出的异常
     */
    @Nullable
    public Object around(ProceedingJoinPoint point) throws Throwable {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        return proceed(point::proceed, method, point.getTarget() == null ? null : point.getTarget().getClass());
    }

    @Override
    @Nullable
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Object target = invocation.getThis();
        return proceed(invocation::proceed, invocation.getMethod(),
            target == null ? null : AopUtils.getTargetClass(target));
    }

    @Nullable
    private Object proceed(ProceedingJoinPointLike point, Method method, @Nullable Class<?> targetClass)
            throws Throwable {
        DataPermission annotation = resolveAnnotation(method, targetClass);
        if (annotation == null) {
            log.warn("[ypbin-starter] 切点命中但无法解析 @DataPermission，已按放行处理（请检查代理类型与注解放置位置）："
                + "method={}", method.getName());
            return point.proceed();
        }
        if (annotation.ignore()) {
            return point.proceed();
        }
        DataPermissionContext.enter();
        try {
            return point.proceed();
        } finally {
            DataPermissionContext.exit();
        }
    }

    /**
     * 解析生效的 {@link DataPermission} 注解：最具体方法 → 目标类 → 接口方法 → 接口类。
     *
     * @param method      调用方看到的方法（可能是接口方法）
     * @param targetClass 目标类（可能为代理类）
     * @return 注解；无时为 {@code null}
     */
    @Nullable
    public static DataPermission resolveAnnotation(Method method, @Nullable Class<?> targetClass) {
        if (targetClass == null) {
            return AnnotatedElementUtils.findMergedAnnotation(method, DataPermission.class);
        }
        Class<?> userClass = ClassUtils.getUserClass(targetClass);
        Method specific = AopUtils.getMostSpecificMethod(method, userClass);
        DataPermission onSpecific = AnnotatedElementUtils.findMergedAnnotation(specific, DataPermission.class);
        if (onSpecific != null) {
            return onSpecific;
        }
        DataPermission onClass = AnnotatedElementUtils.findMergedAnnotation(userClass, DataPermission.class);
        if (onClass != null) {
            return onClass;
        }
        for (Class<?> iface : ClassUtils.getAllInterfacesForClassAsSet(userClass)) {
            DataPermission onInterfaceClass = AnnotatedElementUtils.findMergedAnnotation(iface, DataPermission.class);
            if (onInterfaceClass != null) {
                return onInterfaceClass;
            }
            try {
                Method interfaceMethod = iface.getMethod(method.getName(), method.getParameterTypes());
                DataPermission onInterfaceMethod =
                    AnnotatedElementUtils.findMergedAnnotation(interfaceMethod, DataPermission.class);
                if (onInterfaceMethod != null) {
                    return onInterfaceMethod;
                }
            } catch (NoSuchMethodException ignored) {
                // 该接口没有同名同参方法，继续查找其它接口
            }
        }
        return null;
    }

    /**
     * 可执行的方法体抽象：屏蔽 AspectJ 与 AOP Alliance 两种调用入口的差异。
     */
    @FunctionalInterface
    private interface ProceedingJoinPointLike {

        @Nullable
        Object proceed() throws Throwable;
    }
}

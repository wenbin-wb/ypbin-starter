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

import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.security.core.UserContext;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

/**
 * 平台用户访问守卫切面。
 *
 * <p>拦截标注 {@link PlatformAccess} 的类或方法，校验当前登录用户（经 {@link UserContext}
 * 双形态取值：微服务身份头优先、Sa-Token 会话回退）是否为平台用户；未登录（拿不到 userId）
 * 或非平台用户抛出 {@link BusinessException}（403）。</p>
 *
 * <p><strong>为什么不再用 {@code @Before("@within(...) || @annotation(...)")}</strong>：
 * AspectJ 注解切入点按被调用方法匹配，{@code @within} 只认<strong>目标类</strong>。注解只挂在
 * <strong>接口</strong>上、宿主又采用 JDK 动态代理时，切面完全不织入——平台资源对所有用户开放，
 * 且无任何报错（越权风险）。改为自定义切点（{@link PlatformAccessPointcut}）+
 * {@link MethodInterceptor}，按「实现方法 → 目标类 → 接口方法 → 接口类」逐层解析注解，
 * CGLIB 与 JDK 代理行为一致（与 {@code @DataPermission}、{@code @LicenseCheck} 同一范式）。</p>
 *
 * <p><strong>fail-closed：</strong>业务方未提供 {@link PlatformUserChecker} 实现时，默认判定
 * 为"非平台用户"，标注资源一律拒绝（见 {@link PlatformUserChecker#isPlatformUser}）。</p>
 *
 * <p>本切面由 {@link PlatformAccessAutoConfiguration} 注册为 Bean 并由其
 * {@code platformAccessAdvisor} 挂到容器上（starter 类不在宿主组件扫描范围，不使用 {@code @Component}）。
 * 保留 {@code @Aspect} 仅为标记语义，类上不再声明任何 AspectJ 通知——否则会与 Advisor 重复织入。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
@Aspect
public class PlatformAccessAspect implements MethodInterceptor {

    private static final Logger log = LoggerFactory.getLogger(PlatformAccessAspect.class);

    private final PlatformUserChecker checker;

    public PlatformAccessAspect(PlatformUserChecker checker) {
        this.checker = checker;
    }

    /**
     * 兼容旧命名的直接调用入口（历史签名）：直接调用时总是校验。
     *
     * @param joinPoint 连接点（未使用，保留签名以兼容既有调用方）
     */
    public void guard(JoinPoint joinPoint) {
        checkPlatformAccess();
    }

    @Override
    @Nullable
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Object target = invocation.getThis();
        @Nullable Class<?> targetClass = target == null ? null : AopUtils.getTargetClass(target);
        if (resolveAnnotation(invocation.getMethod(), targetClass) == null) {
            // 切点已匹配却解析不到注解：多为代理/接口组合异常。安全场景必须 fail-closed，
            // 不能当作「无注解」放行——宁可拒绝并留下日志
            log.warn("[ypbin-starter] @PlatformAccess 切点已匹配但无法解析注解，按未授权处理（拒绝）：{}",
                invocation.getMethod().getName());
        }
        checkPlatformAccess();
        return invocation.proceed();
    }

    /**
     * 解析生效的 {@link PlatformAccess} 注解：最具体方法 → 目标类 → 接口方法 → 接口类。
     *
     * @param method      调用方看到的方法（JDK 代理下为接口方法）
     * @param targetClass 目标类（可能为代理类，可空）
     * @return 注解；无时为 {@code null}
     */
    static @Nullable PlatformAccess resolveAnnotation(Method method, @Nullable Class<?> targetClass) {
        if (targetClass == null) {
            return AnnotatedElementUtils.findMergedAnnotation(method, PlatformAccess.class);
        }
        Class<?> userClass = ClassUtils.getUserClass(targetClass);
        Method specific = AopUtils.getMostSpecificMethod(method, userClass);
        PlatformAccess onSpecific = AnnotatedElementUtils.findMergedAnnotation(specific, PlatformAccess.class);
        if (onSpecific != null) {
            return onSpecific;
        }
        PlatformAccess onClass = AnnotatedElementUtils.findMergedAnnotation(userClass, PlatformAccess.class);
        if (onClass != null) {
            return onClass;
        }
        for (Class<?> iface : ClassUtils.getAllInterfacesForClassAsSet(userClass)) {
            PlatformAccess onInterfaceClass = AnnotatedElementUtils.findMergedAnnotation(iface, PlatformAccess.class);
            if (onInterfaceClass != null) {
                return onInterfaceClass;
            }
            try {
                Method interfaceMethod = iface.getMethod(method.getName(), method.getParameterTypes());
                PlatformAccess onInterfaceMethod =
                    AnnotatedElementUtils.findMergedAnnotation(interfaceMethod, PlatformAccess.class);
                if (onInterfaceMethod != null) {
                    return onInterfaceMethod;
                }
            } catch (NoSuchMethodException ignored) {
                // 该接口没有同名同参方法，继续查找其它接口
            }
        }
        return null;
    }

    private void checkPlatformAccess() {
        // 双形态取当前用户：微服务（网关身份头）与单体（sa-token 会话）都能拿到真实 userId，
        // 仅真正未登录时为空 —— 避免身份头默认关闭后单体形态的平台资源被误拒
        Long userId = UserContext.getUserId();
        if (userId == null) {
            // 未登录/身份缺失：直接拒绝，不把 null 交给判定器（避免默认实现误放行）
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "未登录，禁止访问平台资源");
        }
        if (!checker.isPlatformUser(userId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN, "仅平台用户可访问");
        }
    }
}

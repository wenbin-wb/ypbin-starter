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
package cn.ypbin.starter.security.satoken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.ypbin.starter.security.autoconfigure.SecurityProperties;
import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.core.PermissionProvider;
import cn.ypbin.starter.security.identity.IdentityContext;
import cn.ypbin.starter.security.identity.IdentityStpLogic;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

/**
 * {@link SaTokenWebConfigurer} 的开关组合测试。
 *
 * <p>锁定 {@code ypbin.security.interceptor}（登录拦截）与 {@code ypbin.security.annotation-check}
 * （注解鉴权）<strong>相互独立</strong>这一修复：此前两者由一个开关承载，微服务下游为了关掉必然失败的
 * 登录校验只能把注解鉴权一起关掉。四个组合在此逐个钉住，且都通过真实的
 * {@link SaInterceptor#preHandle} 行为断言（而不是只看注册数量）。</p>
 *
 * @author wenbin
 * @since 2026-09-24
 */
class SaTokenWebConfigurerTest {

    private StpLogic originalStpLogic;
    private StpInterface originalStpInterface;

    @BeforeEach
    void setUp() {
        // Sa-Token 的 StpLogic 会读写请求级 Storage（如 getLoginId() 首行的 isSwitch()）：
        // 纯单元测试没有 Servlet 请求上下文，用 Sa-Token 官方 Mock 上下文补齐
        SaTokenContextMockUtil.setMockContext();
        originalStpLogic = StpUtil.stpLogic;
        originalStpInterface = SaManager.getStpInterface();
        StpUtil.setStpLogic(new IdentityStpLogic());
        SaManager.setStpInterface(new StpPermissionAdapter(new StubPermissionProvider()));
    }

    @AfterEach
    void tearDown() {
        StpUtil.setStpLogic(originalStpLogic);
        SaManager.setStpInterface(originalStpInterface);
        IdentityContext.clear();
        SaTokenContextMockUtil.clearContext();
    }

    @Test
    @DisplayName("interceptor=true 且 annotation-check=true（默认）：登录校验与注解鉴权都生效")
    void bothEnabled_default() throws Exception {
        SaInterceptor interceptor = registered(properties(true, true));

        assertThatThrownBy(() -> interceptor.preHandle(request(DispatcherType.REQUEST), response(),
            handler("plain")))
            .as("无身份头时登录校验必须拒绝")
            .isInstanceOf(NotLoginException.class);
        IdentityContext.setLoginUser(loginUser(42L));
        assertThat(interceptor.preHandle(request(DispatcherType.REQUEST), response(), handler("plain")))
            .as("有身份头时普通端点放行")
            .isTrue();
        assertThatThrownBy(() -> interceptor.preHandle(request(DispatcherType.REQUEST), response(),
            handler("deleteDevice")))
            .as("有身份头但无权限：注解鉴权必须拒绝")
            .isInstanceOf(NotPermissionException.class);
    }

    @Test
    @DisplayName("interceptor=false 且 annotation-check=true：只关登录校验，注解鉴权仍然生效")
    void annotationCheckSurvivesInterceptorOff() throws Exception {
        SaInterceptor interceptor = registered(properties(false, true));

        assertThat(interceptor.preHandle(request(DispatcherType.REQUEST), response(), handler("plain")))
            .as("登录校验关闭：无身份头的普通端点放行")
            .isTrue();
        assertThatThrownBy(() -> interceptor.preHandle(request(DispatcherType.REQUEST), response(),
            handler("openWindow")))
            .as("注解鉴权仍在：无身份头时带权限注解的端点必须拒绝")
            .isInstanceOf(NotLoginException.class);
        IdentityContext.setLoginUser(loginUser(42L));
        assertThat(interceptor.preHandle(request(DispatcherType.REQUEST), response(), handler("openWindow")))
            .as("注解鉴权仍在：有身份头且权限足够时放行")
            .isTrue();
        assertThatThrownBy(() -> interceptor.preHandle(request(DispatcherType.REQUEST), response(),
            handler("deleteDevice")))
            .as("注解鉴权仍在：有身份头但权限不足时拒绝")
            .isInstanceOf(NotPermissionException.class);
    }

    @Test
    @DisplayName("interceptor=true 且 annotation-check=false：只关注解鉴权，登录校验仍然生效")
    void interceptorSurvivesAnnotationCheckOff() throws Exception {
        SaInterceptor interceptor = registered(properties(true, false));

        assertThatThrownBy(() -> interceptor.preHandle(request(DispatcherType.REQUEST), response(),
            handler("plain")))
            .as("登录校验仍在")
            .isInstanceOf(NotLoginException.class);
        IdentityContext.setLoginUser(loginUser(42L));
        assertThat(interceptor.preHandle(request(DispatcherType.REQUEST), response(), handler("deleteDevice")))
            .as("注解鉴权关闭：无权限也放行")
            .isTrue();
    }

    @Test
    @DisplayName("两个开关都关闭：不注册任何拦截器")
    void bothDisabled_registersNothing() {
        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        new SaTokenWebConfigurer(properties(false, false), List.of()).addInterceptors(registry);

        verify(registry, never()).addInterceptor(any(HandlerInterceptor.class));
    }

    @Test
    @DisplayName("非 REQUEST 分发（ASYNC/ERROR）直接放行，避免异步流二次校验误报")
    void nonRequestDispatch_passesThrough() throws Exception {
        SaInterceptor interceptor = registered(properties(true, true));

        assertThat(interceptor.preHandle(request(DispatcherType.ASYNC), response(), handler("plain")))
            .isTrue();
        assertThat(interceptor.preHandle(request(DispatcherType.ERROR), response(), handler("openWindow")))
            .isTrue();
    }

    private static SecurityProperties properties(boolean interceptor, boolean annotationCheck) {
        SecurityProperties properties = new SecurityProperties();
        properties.setInterceptor(interceptor);
        properties.setAnnotationCheck(annotationCheck);
        return properties;
    }

    private static SaInterceptor registered(SecurityProperties properties) {
        // 拦截器实例从 addInterceptor(...) 的入参捕获：Spring 6 的 InterceptorRegistry#getInterceptors 是 protected，
        // 捕获参数比反射读内部结构更稳，也不依赖 MappedInterceptor 的包装细节
        InterceptorRegistry registry = mock(InterceptorRegistry.class);
        InterceptorRegistration registration = mock(InterceptorRegistration.class);
        when(registry.addInterceptor(any(HandlerInterceptor.class))).thenReturn(registration);
        when(registration.addPathPatterns(anyList())).thenReturn(registration);
        when(registration.excludePathPatterns(anyList())).thenReturn(registration);

        new SaTokenWebConfigurer(properties, List.of()).addInterceptors(registry);

        ArgumentCaptor<HandlerInterceptor> captor = ArgumentCaptor.forClass(HandlerInterceptor.class);
        verify(registry).addInterceptor(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(SaInterceptor.class);
        return (SaInterceptor) captor.getValue();
    }

    private static HttpServletRequest request(DispatcherType dispatcherType) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getDispatcherType()).thenReturn(dispatcherType);
        return request;
    }

    private static HttpServletResponse response() {
        return mock(HttpServletResponse.class);
    }

    private static HandlerMethod handler(String methodName) throws NoSuchMethodException {
        Method method = DemoController.class.getMethod(methodName);
        return new HandlerMethod(new DemoController(), method);
    }

    private static LoginUser loginUser(Long id) {
        LoginUser user = new LoginUser();
        user.setId(id);
        return user;
    }

    /** 鉴权目标：plain 无注解，openWindow/deleteDevice 带权限码。 */
    static class DemoController {

        public String plain() {
            return "ok";
        }

        @SaCheckPermission("iot:window:add")
        public String openWindow() {
            return "ok";
        }

        @SaCheckPermission("iot:device:delete")
        public String deleteDevice() {
            return "ok";
        }
    }

    /** 权限数据源桩：账号 42 只有 iot:window:add。 */
    static class StubPermissionProvider implements PermissionProvider {

        @Override
        public List<String> getPermissions(Object loginId, String loginType) {
            if ("42".equals(String.valueOf(loginId))) {
                return List.of("iot:window:add");
            }
            return List.of();
        }
    }
}

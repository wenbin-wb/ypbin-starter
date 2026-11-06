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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.security.identity.IdentityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * {@link PlatformAccess} 在「CGLIB 代理」下的真实织入用例（注解位置 × 代理方式矩阵的一半）。
 *
 * <p>已有的 {@link PlatformAccessAspectTest} 直接调用 {@code aspect.guard(joinPoint())}，只能证明
 * 方法体逻辑正确，证明不了切点真的命中目标方法——PITFALL 9 记过的正是这种「单测全绿、线上静默放行」。
 * 这里启动真实容器，断言：标注了注解的方法被拒绝，未标注的方法正常执行。</p>
 *
 * <p>拒绝以 {@link BusinessException} 表达即可证明切面已生效（测试未登录，fail-closed 必然拒绝），
 * 不需要构造登录态。</p>
 *
 * @author wenbin
 * @since 2026-11-06
 */
@SpringBootTest(classes = PlatformAccessWeavingCglibTest.App.class)
class PlatformAccessWeavingCglibTest {

    /** 方法级注解 */
    static class MethodAnnotatedService {

        @PlatformAccess
        public String guarded() {
            return "guarded";
        }

        public String plain() {
            return "plain";
        }
    }

    /** 类级注解 */
    @PlatformAccess
    static class TypeAnnotatedService {

        public String guarded() {
            return "guarded";
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class App {

        @Bean
        PlatformUserChecker platformUserChecker() {
            return new PlatformUserChecker() {
            };
        }

        @Bean
        PlatformAccessAspect platformAccessAspect(PlatformUserChecker checker) {
            return new PlatformAccessAspect(checker);
        }

        @Bean
        Advisor platformAccessAdvisor(PlatformAccessAspect aspect) {
            return new DefaultPointcutAdvisor(new PlatformAccessPointcut(), aspect);
        }

        @Bean
        MethodAnnotatedService methodAnnotatedService() {
            return new MethodAnnotatedService();
        }

        @Bean
        TypeAnnotatedService typeAnnotatedService() {
            return new TypeAnnotatedService();
        }
    }

    @Autowired
    private MethodAnnotatedService methodAnnotatedService;

    @Autowired
    private TypeAnnotatedService typeAnnotatedService;

    @BeforeEach
    void clearIdentity() {
        IdentityContext.clear();
    }

    @Test
    @DisplayName("方法级注解 × CGLIB：命中并拒绝")
    void methodAnnotationShouldBeEnforcedUnderCglib() {
        assertThat(AopUtils.isCglibProxy(methodAnnotatedService)).as("应使用 CGLIB 代理").isTrue();

        assertThatThrownBy(methodAnnotatedService::guarded).isInstanceOf(BusinessException.class);
        assertThat(methodAnnotatedService.plain())
            .as("未标注的方法不得被拦截")
            .isEqualTo("plain");
    }

    @Test
    @DisplayName("类级注解 × CGLIB：命中并拒绝")
    void typeAnnotationShouldBeEnforcedUnderCglib() {
        assertThatThrownBy(typeAnnotatedService::guarded).isInstanceOf(BusinessException.class);
    }
}

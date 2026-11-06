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
 * {@link PlatformAccess} 在「JDK 动态代理」下的真实织入用例（注解位置 × 代理方式矩阵的另一半）。
 *
 * <p>JDK 代理下调用方看到的是<strong>接口方法</strong>：注解只写在实现类方法上时，标准
 * {@code @annotation} 表达式必须经「最具体方法」解析才命中；注解写在<strong>接口</strong>上时，
 * 则依赖 {@code @within}/{@code @annotation} 对接口声明的处理。这两条正是历史上静默失效的形态，
 * 故逐条用真实容器断言。</p>
 *
 * @author wenbin
 * @since 2026-11-06
 */
@SpringBootTest(classes = PlatformAccessWeavingJdkProxyTest.App.class)
class PlatformAccessWeavingJdkProxyTest {

    interface PlainApi {

        String guarded();
    }

    static class MethodAnnotatedImpl implements PlainApi {

        @Override
        @PlatformAccess
        public String guarded() {
            return "guarded";
        }
    }

    /** 注解写在接口类上：JDK 代理下目标类本身没有注解。 */
    @PlatformAccess
    interface TypeAnnotatedApi {

        String guarded();
    }

    static class TypeAnnotatedImpl implements TypeAnnotatedApi {

        @Override
        public String guarded() {
            return "guarded";
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
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
        PlainApi plainApi() {
            return new MethodAnnotatedImpl();
        }

        @Bean
        TypeAnnotatedApi typeAnnotatedApi() {
            return new TypeAnnotatedImpl();
        }
    }

    @Autowired
    private PlainApi plainApi;

    @Autowired
    private TypeAnnotatedApi typeAnnotatedApi;

    @BeforeEach
    void clearIdentity() {
        IdentityContext.clear();
    }

    @Test
    @DisplayName("实现类方法注解 × JDK 代理：命中并拒绝")
    void implementationMethodAnnotationShouldBeEnforcedUnderJdkProxy() {
        assertThat(AopUtils.isJdkDynamicProxy(plainApi)).as("应使用 JDK 动态代理").isTrue();

        assertThatThrownBy(plainApi::guarded).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("接口类注解 × JDK 代理：命中并拒绝")
    void interfaceTypeAnnotationShouldBeEnforcedUnderJdkProxy() {
        assertThatThrownBy(typeAnnotatedApi::guarded).isInstanceOf(BusinessException.class);
    }
}

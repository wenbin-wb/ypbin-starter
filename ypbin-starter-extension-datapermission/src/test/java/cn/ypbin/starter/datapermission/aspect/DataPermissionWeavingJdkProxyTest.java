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

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.datapermission.annotation.DataPermission;
import cn.ypbin.starter.datapermission.core.DataPermissionContext;
import org.junit.jupiter.api.BeforeEach;
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
 * 「实现类方法注解 × JDK 代理」的真实 Spring 织入用例。
 *
 * <p>与 {@link DataPermissionWeavingCglibTest} 互为对照：注解只出现在实现类方法上时，调用方看到的是
 * 接口方法，切点必须经 {@code AopUtils.getMostSpecificMethod} 找到实现类注解才能命中。</p>
 *
 * @author wenbin
 * @since 2026-11-06
 */
@SpringBootTest(classes = DataPermissionWeavingJdkProxyTest.App.class)
class DataPermissionWeavingJdkProxyTest {

    private static volatile boolean activeDuringCall;

    interface PlainApi {

        String call();
    }

    static class AnnotatedImpl implements PlainApi {

        @Override
        @DataPermission
        public String call() {
            activeDuringCall = DataPermissionContext.isActive();
            return "ok";
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = false)
    static class App {

        @Bean
        DataPermissionAspect dataPermissionAspect() {
            return new DataPermissionAspect();
        }

        @Bean
        Advisor dataPermissionAdvisor(DataPermissionAspect aspect) {
            return new DefaultPointcutAdvisor(new DataPermissionPointcut(), aspect);
        }

        @Bean
        PlainApi plainApi() {
            return new AnnotatedImpl();
        }
    }

    @Autowired
    private PlainApi plainApi;

    @BeforeEach
    void resetProbe() {
        activeDuringCall = false;
    }

    @Test
    void implementationAnnotationShouldActivateScopeUnderJdkProxy() {
        assertThat(AopUtils.isJdkDynamicProxy(plainApi)).as("应使用 JDK 动态代理").isTrue();

        assertThat(plainApi.call()).isEqualTo("ok");

        assertThat(activeDuringCall).as("实现类方法上的 @DataPermission 必须激活数据权限作用域").isTrue();
        assertThat(DataPermissionContext.isActive()).as("方法返回后作用域必须退出").isFalse();
    }
}

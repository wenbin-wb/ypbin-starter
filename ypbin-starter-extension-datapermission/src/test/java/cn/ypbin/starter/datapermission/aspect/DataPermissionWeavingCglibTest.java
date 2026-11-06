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
 * 「接口类注解 × CGLIB 代理」的真实 Spring 织入用例。
 *
 * <p>{@link DataPermissionAspectTest} 只做 {@code resolveAnnotation} 的静态断言，证明不了解同一个
 * 注解在真实容器里是否被切点命中——这正是 PITFALL 9 记过的静默失效形态。本类启动真实容器，
 * 断言调用期间 {@link DataPermissionContext#isActive()} 为 {@code true}，且方法返回后作用域已退出。</p>
 *
 * @author wenbin
 * @since 2026-11-06
 */
@SpringBootTest(classes = DataPermissionWeavingCglibTest.App.class)
class DataPermissionWeavingCglibTest {

    /** 方法执行期间观察到的作用域状态（静态持有，避免 CGLIB 代理与目标字段不同实例）。 */
    private static volatile boolean activeDuringCall;

    /** 注解只挂在接口类上：历史上 JDK 代理下完全不织入。 */
    @DataPermission
    interface GuardedApi {

        String call();
    }

    static class GuardedApiImpl implements GuardedApi {

        @Override
        public String call() {
            activeDuringCall = DataPermissionContext.isActive();
            return "ok";
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy(proxyTargetClass = true)
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
        GuardedApi guardedApi() {
            return new GuardedApiImpl();
        }
    }

    @Autowired
    private GuardedApi guardedApi;

    @BeforeEach
    void resetProbe() {
        activeDuringCall = false;
    }

    @Test
    void interfaceAnnotationShouldActivateScopeUnderCglibProxy() {
        assertThat(AopUtils.isCglibProxy(guardedApi)).as("应使用 CGLIB 代理").isTrue();

        assertThat(guardedApi.call()).isEqualTo("ok");

        assertThat(activeDuringCall).as("接口类上的 @DataPermission 必须激活数据权限作用域").isTrue();
        assertThat(DataPermissionContext.isActive()).as("方法返回后作用域必须退出").isFalse();
    }
}

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
package cn.ypbin.starter.tenant.aspect;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.tenant.annotation.TenantIgnore;
import cn.ypbin.starter.tenant.core.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * {@link TenantIgnoreAspect} 真实 AOP 代理织入测试。
 *
 * @author wenbin
 * @since 2026-07-30
 */
class TenantIgnoreAspectTest {

    private AnnotationConfigApplicationContext ctx;
    private DemoService service;

    @BeforeEach
    void setUp() {
        ctx = new AnnotationConfigApplicationContext(Config.class);
        service = ctx.getBean(DemoService.class);
    }

    @AfterEach
    void tearDown() {
        if (ctx != null) {
            ctx.close();
        }
    }

    @Test
    void ignoredMethod_activatesContextDuringExecution() {
        assertThat(TenantContext.isIgnored()).isFalse();
        boolean insideIgnored = service.query();
        assertThat(insideIgnored).isTrue();
        // 方法退出后自动恢复
        assertThat(TenantContext.isIgnored()).isFalse();
    }

    @Test
    void normalMethod_doesNotActivate() {
        assertThat(service.normal()).isFalse();
    }

    @Test
    void nestedIgnore_restoresProperly() {
        // outer 已激活忽略，内部再调一个被代理的忽略方法，验证内层退出不误关闭外层
        assertThat(service.outer()).isTrue();
        assertThat(TenantContext.isIgnored()).isFalse();
    }

    @EnableAspectJAutoProxy
    static class Config {
        @Bean
        TenantIgnoreAspect tenantIgnoreAspect() {
            return new TenantIgnoreAspect();
        }

        @Bean
        InnerService innerService() {
            return new InnerService();
        }

        @Bean
        DemoService demoService(InnerService inner) {
            return new DemoService(inner);
        }
    }

    static class DemoService {
        private final InnerService inner;

        DemoService(InnerService inner) {
            this.inner = inner;
        }

        @TenantIgnore
        public boolean query() {
            return TenantContext.isIgnored();
        }

        public boolean normal() {
            return TenantContext.isIgnored();
        }

        // 外层忽略，经代理调用另一忽略方法（真实触发嵌套切面），验证内层退出不误关闭外层
        @TenantIgnore
        public boolean outer() {
            boolean before = TenantContext.isIgnored();
            inner.inner();
            boolean after = TenantContext.isIgnored();
            return before && after;
        }
    }

    static class InnerService {
        @TenantIgnore
        public void inner() {
            // 内层方法退出不应关闭外层的忽略状态（计数式恢复）
        }
    }
}

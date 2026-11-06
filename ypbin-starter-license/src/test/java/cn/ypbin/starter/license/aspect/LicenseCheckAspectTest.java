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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.license.annotation.LicenseCheck;
import cn.ypbin.starter.license.core.LicenseContent;
import cn.ypbin.starter.license.core.LicenseManager;
import cn.ypbin.starter.license.core.LicenseSigner;
import cn.ypbin.starter.license.exception.LicenseException;
import cn.ypbin.starter.tools.crypto.Sm2Utils;
import cn.ypbin.starter.tools.crypto.Sm2Utils.KeyPairBase64;
import cn.ypbin.starter.tools.crypto.Sm4Utils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * {@link LicenseCheckAspect} 真实 AOP 代理织入测试。
 *
 * <p>覆盖「注解位置 × 代理方式」四种组合，防止只在实现类 + CGLIB 下有效的静默失效。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
class LicenseCheckAspectTest {

    private static final KeyPairBase64 SM2 = Sm2Utils.generateKeyPair();
    private static final String SM4 = Sm4Utils.generateKeyBase64();

    private AnnotationConfigApplicationContext ctx;

    @AfterEach
    void tearDown() {
        if (ctx != null) {
            ctx.close();
        }
    }

    private <T> T service(LicenseContent content, Class<?> config, Class<T> type) {
        ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(LicenseManager.class, () -> {
            LicenseManager manager = new LicenseManager(SM2.publicKey(), SM4, false);
            manager.load(LicenseSigner.issue(content, SM2.privateKey(), SM4));
            return manager;
        });
        ctx.register(config);
        ctx.refresh();
        return ctx.getBean(type);
    }

    private LicenseContent content(LocalDateTime expireAt, List<String> modules) {
        return new LicenseContent("LIC-ASPECT", "切面测试", "",
            List.of(), null,
            LocalDateTime.now(), LocalDateTime.now().minusDays(1), expireAt, 0,
            modules, Map.of(), Map.of());
    }

    @Test
    void implementationAnnotationWithCglib() {
        ProtectedService service = service(content(LocalDateTime.now().plusDays(30), List.of("report")),
            CglibConfig.class, ProtectedService.class);

        assertThat(service.basic()).isEqualTo("ok");
        assertThat(service.reportModule()).isEqualTo("report-ok");
    }

    @Test
    void implementationAnnotationWithCglibRejectsExpired() {
        ProtectedService service = service(content(LocalDateTime.now().minusDays(1), List.of()),
            CglibConfig.class, ProtectedService.class);

        assertThatThrownBy(service::basic).isInstanceOf(LicenseException.class);
    }

    @Test
    void interfaceAnnotationWithCglibRejectsExpired() {
        ProtectedApi service = service(content(LocalDateTime.now().minusDays(1), List.of()),
            CglibConfig.class, ProtectedApi.class);

        assertThatThrownBy(service::basic).isInstanceOf(LicenseException.class);
    }

    @Test
    void interfaceAnnotationWithJdkProxyRejectsExpired() {
        ProtectedApi service = service(content(LocalDateTime.now().minusDays(1), List.of()),
            JdkProxyConfig.class, ProtectedApi.class);

        assertThatThrownBy(service::basic).isInstanceOf(LicenseException.class);
    }

    @Test
    void implementationAnnotationWithJdkProxyRejectsExpired() {
        PlainApi service = service(content(LocalDateTime.now().minusDays(1), List.of()),
            JdkProxyConfig.class, PlainApi.class);

        assertThatThrownBy(service::basic).isInstanceOf(LicenseException.class);
    }

    @Test
    void moduleCheckStillAppliesOnInterfaceAnnotation() {
        ProtectedApi service = service(
            content(LocalDateTime.now().plusDays(30), List.of("export")),
            CglibConfig.class, ProtectedApi.class);

        assertThatThrownBy(service::reportModule).isInstanceOf(LicenseException.class);
    }

    private static LicenseCheckAspect aspect(LicenseManager manager) {
        return new LicenseCheckAspect(manager, List.of());
    }

    private static Advisor advisor(LicenseCheckAspect aspect) {
        return new DefaultPointcutAdvisor(new LicenseCheckPointcut(), aspect);
    }

    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class CglibConfig {
        @Bean
        LicenseCheckAspect licenseCheckAspect(LicenseManager manager) {
            return aspect(manager);
        }

        @Bean
        Advisor licenseCheckAdvisor(LicenseCheckAspect aspect) {
            return advisor(aspect);
        }

        @Bean
        ProtectedService protectedService() {
            return new ProtectedService();
        }

        @Bean
        InterfaceAnnotatedService interfaceAnnotatedService() {
            return new InterfaceAnnotatedService();
        }
    }

    @EnableAspectJAutoProxy(proxyTargetClass = false)
    static class JdkProxyConfig {
        @Bean
        LicenseCheckAspect licenseCheckAspect(LicenseManager manager) {
            return aspect(manager);
        }

        @Bean
        Advisor licenseCheckAdvisor(LicenseCheckAspect aspect) {
            return advisor(aspect);
        }

        @Bean
        ProtectedApi protectedApi() {
            // 实现类方法无注解，注解只在接口上
            return new InterfaceAnnotatedService();
        }

        @Bean
        PlainApi plainApi() {
            // 接口无注解，注解只在实现类方法上
            return new ImplementationAnnotatedService();
        }
    }

    interface ProtectedApi {
        @LicenseCheck
        String basic();

        @LicenseCheck(module = "report")
        String reportModule();
    }

    interface PlainApi {
        String basic();
    }

    static class ImplementationAnnotatedService implements PlainApi {
        @Override
        @LicenseCheck
        public String basic() {
            return "ok";
        }
    }

    static class InterfaceAnnotatedService implements ProtectedApi {
        @Override
        public String basic() {
            return "ok";
        }

        @Override
        public String reportModule() {
            return "report-ok";
        }
    }

    static class ProtectedService {
        @LicenseCheck
        public String basic() {
            return "ok";
        }

        @LicenseCheck(module = "report")
        public String reportModule() {
            return "report-ok";
        }
    }
}

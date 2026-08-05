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
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * {@link LicenseCheckAspect} 真实 AOP 代理织入测试。
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

    private ProtectedService service(LicenseContent content) {
        ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(LicenseManager.class, () -> {
            LicenseManager manager = new LicenseManager(SM2.publicKey(), SM4, false);
            manager.load(LicenseSigner.issue(content, SM2.privateKey(), SM4));
            return manager;
        });
        ctx.register(Config.class);
        ctx.refresh();
        return ctx.getBean(ProtectedService.class);
    }

    private LicenseContent content(LocalDateTime expireAt, List<String> modules) {
        return new LicenseContent("LIC-ASPECT", "切面测试", "",
            List.of(), null,
            LocalDateTime.now(), LocalDateTime.now().minusDays(1), expireAt, 0,
            modules, Map.of(), Map.of());
    }

    @Test
    void shouldPass_whenLicenseUsable() {
        ProtectedService service = service(content(LocalDateTime.now().plusDays(30), List.of("report")));

        assertThat(service.basic()).isEqualTo("ok");
        assertThat(service.reportModule()).isEqualTo("report-ok");
    }

    @Test
    void shouldReject_whenExpired() {
        ProtectedService service = service(content(LocalDateTime.now().minusDays(1), List.of("report")));

        assertThatThrownBy(service::basic).isInstanceOf(LicenseException.class);
    }

    @Test
    void shouldReject_whenModuleUnlicensed() {
        ProtectedService service = service(content(LocalDateTime.now().plusDays(30), List.of("export")));

        // 基础可用性通过，但 report 模块未授权
        assertThat(service.basic()).isEqualTo("ok");
        assertThatThrownBy(service::reportModule).isInstanceOf(LicenseException.class);
    }

    @EnableAspectJAutoProxy
    static class Config {
        @Bean
        LicenseCheckAspect licenseCheckAspect(LicenseManager manager) {
            return new LicenseCheckAspect(manager, List.of());
        }

        @Bean
        ProtectedService protectedService() {
            return new ProtectedService();
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

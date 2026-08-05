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
package cn.ypbin.starter.license.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.license.aspect.LicenseCheckAspect;
import cn.ypbin.starter.license.core.LicenseContent;
import cn.ypbin.starter.license.core.LicenseManager;
import cn.ypbin.starter.license.core.LicenseSigner;
import cn.ypbin.starter.license.core.LicenseStatus;
import cn.ypbin.starter.license.extension.LicenseStore;
import cn.ypbin.starter.tools.crypto.Sm2Utils;
import cn.ypbin.starter.tools.crypto.Sm2Utils.KeyPairBase64;
import cn.ypbin.starter.tools.crypto.Sm4Utils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * {@link LicenseAutoConfiguration} 装配测试。
 *
 * @author wenbin
 * @since 2026-08-05
 */
class LicenseAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(LicenseAutoConfiguration.class));

    private Path writeLicense(Path dir) throws IOException {
        KeyPairBase64 sm2 = Sm2Utils.generateKeyPair();
        String sm4 = Sm4Utils.generateKeyBase64();
        LicenseContent content = new LicenseContent("LIC-AC", "装配测试", "",
            List.of(), null,
            LocalDateTime.now(), LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30), 0,
            List.of(), Map.of(), Map.of());
        String authCode = LicenseSigner.issue(content, sm2.privateKey(), sm4);
        Path file = dir.resolve("license.dat");
        Files.writeString(file, authCode, StandardCharsets.UTF_8);
        // 密钥随文件返回，供属性配置使用
        this.publicKey = sm2.publicKey();
        this.secretKey = sm4;
        return file;
    }

    private String publicKey;
    private String secretKey;

    @Test
    void shouldAssembleAndLoadLegalLicense(@TempDir Path dir) throws IOException {
        Path file = writeLicense(dir);
        runner.withPropertyValues(
                "ypbin.license.public-key=" + publicKey,
                "ypbin.license.secret-key=" + secretKey,
                "ypbin.license.fingerprint-enabled=false",
                "ypbin.license.location=" + file.toString().replace("\\", "/"))
            .run(context -> {
                assertThat(context).hasSingleBean(LicenseManager.class);
                assertThat(context).hasSingleBean(LicenseCheckAspect.class);
                assertThat(context).hasSingleBean(LicenseStore.class);
                assertThat(context.getBean(LicenseManager.class).getStatus()).isEqualTo(LicenseStatus.LEGAL);
            });
    }

    @Test
    void shouldFailStartup_whenNoLicenseAndNotAllowed(@TempDir Path dir) {
        runner.withPropertyValues(
                "ypbin.license.public-key=" + Sm2Utils.generateKeyPair().publicKey(),
                "ypbin.license.secret-key=" + Sm4Utils.generateKeyBase64(),
                "ypbin.license.location=" + dir.resolve("absent.dat").toString().replace("\\", "/"))
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldStartIllegal_whenNoLicenseButAllowed(@TempDir Path dir) {
        runner.withPropertyValues(
                "ypbin.license.public-key=" + Sm2Utils.generateKeyPair().publicKey(),
                "ypbin.license.secret-key=" + Sm4Utils.generateKeyBase64(),
                "ypbin.license.allow-startup-without-license=true",
                "ypbin.license.location=" + dir.resolve("absent.dat").toString().replace("\\", "/"))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(LicenseManager.class).getStatus()).isEqualTo(LicenseStatus.ILLEGAL);
            });
    }

    @Test
    void shouldBackOffWhenDisabled() {
        runner.withPropertyValues("ypbin.license.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(LicenseManager.class));
    }
}

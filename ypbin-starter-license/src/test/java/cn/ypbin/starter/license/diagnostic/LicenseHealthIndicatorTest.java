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
package cn.ypbin.starter.license.diagnostic;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.license.core.LicenseContent;
import cn.ypbin.starter.license.core.LicenseManager;
import cn.ypbin.starter.license.core.LicenseSigner;
import cn.ypbin.starter.license.core.LicenseStatus;
import cn.ypbin.starter.tools.crypto.Sm2Utils;
import cn.ypbin.starter.tools.crypto.Sm2Utils.KeyPairBase64;
import cn.ypbin.starter.tools.crypto.Sm4Utils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

/**
 * {@link LicenseHealthIndicator} 测试：锁定状态映射与「证书剩余有效天数」口径。
 *
 * @author wenbin
 * @since 2026-10-17
 */
class LicenseHealthIndicatorTest {

    private static final String LICENSE_ID = "LIC-TEST-0001";

    private final KeyPairBase64 sm2 = Sm2Utils.generateKeyPair();
    private final String sm4 = Sm4Utils.generateKeyBase64();

    private LicenseManager licensedManager(int effectiveDaysAgo, int expireDaysAhead, int graceDays) {
        LicenseContent content = new LicenseContent(LICENSE_ID, "测试被授权方", "单元测试",
            List.of(), null,
            LocalDateTime.now().minusDays(30),
            LocalDateTime.now().minusDays(effectiveDaysAgo),
            LocalDateTime.now().plusDays(expireDaysAhead),
            graceDays,
            List.of("report"),
            Map.of("device", 100L),
            Map.of("region", "cn"));
        LicenseManager manager = new LicenseManager(sm2.publicKey(), sm4, false);
        manager.load(LicenseSigner.issue(content, sm2.privateKey(), sm4));
        return manager;
    }

    @Test
    @DisplayName("合法授权：UP + 剩余自然日数 + 宽限后自然日数")
    void legalLicenseReportsRemainingDays() {
        Health health = new LicenseHealthIndicator(licensedManager(1, 30, 7)).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("status", LicenseStatus.LEGAL.name())
            .containsEntry("licenseLoaded", true)
            .containsEntry("licenseId", LICENSE_ID)
            .containsEntry("graceDays", 7)
            .containsEntry("remainingDays", 30L)
            .containsEntry("graceRemainingDays", 37L);
    }

    @Test
    @DisplayName("宽限期内：对外仍是 UP（应用可用），但 details 显式给出 GRACE 与宽限剩余天数")
    void graceLicenseStaysUpButIsFlagged() {
        Health health = new LicenseHealthIndicator(licensedManager(40, -1, 7)).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("status", LicenseStatus.GRACE.name())
            .containsEntry("remainingDays", -1L)
            .containsEntry("graceRemainingDays", 6L);
    }

    @Test
    @DisplayName("超出宽限期：DOWN + 剩余天数为负")
    void illegalLicenseIsDown() {
        Health health = new LicenseHealthIndicator(licensedManager(40, -10, 3)).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("status", LicenseStatus.ILLEGAL.name());
        assertThat((Long) health.getDetails().get("remainingDays")).isNegative();
        assertThat((Long) health.getDetails().get("graceRemainingDays")).isNegative();
    }

    @Test
    @DisplayName("未加载授权（无授权启动）：DOWN 且标记 licenseLoaded=false")
    void unloadedLicenseIsDown() {
        LicenseManager manager = new LicenseManager(sm2.publicKey(), sm4, false);

        Health health = new LicenseHealthIndicator(manager).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("licenseLoaded", false);
    }

    @Test
    @DisplayName("★ 不泄漏敏感信息：details 不含密钥/授权串原文")
    void detailsDoNotLeakSecrets() {
        Health health = new LicenseHealthIndicator(licensedManager(1, 30, 0)).health();

        assertThat(health.getDetails().values())
            .as("健康端点可能被外部采集，绝不输出密钥类信息")
            .doesNotContain(sm4, sm2.privateKey(), sm2.publicKey());
    }
}

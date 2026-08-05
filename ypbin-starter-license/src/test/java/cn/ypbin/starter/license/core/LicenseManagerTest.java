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
package cn.ypbin.starter.license.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.license.exception.LicenseException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * {@link LicenseManager} 离线校验状态机单元测试，覆盖合法/宽限/过期/未生效四态与模块、额度断言。
 *
 * @author wenbin
 * @since 2026-08-05
 */
class LicenseManagerTest {

    private final LicenseTestKeys keys = new LicenseTestKeys();

    private LicenseManager manager() {
        // 指纹绑定关闭：本测试聚焦时间/模块/额度状态机；指纹匹配另有专测
        return new LicenseManager(keys.sm2.publicKey(), keys.sm4, false);
    }

    @Test
    void legal_whenWithinValidPeriod() {
        LicenseManager manager = manager();
        manager.load(keys.issue(keys.content(LocalDateTime.now().minusDays(1),
            LocalDateTime.now().plusDays(30), 7)));

        assertThat(manager.getStatus()).isEqualTo(LicenseStatus.LEGAL);
        manager.assertUsable();
    }

    @Test
    void grace_whenExpiredButWithinGraceDays() {
        LicenseManager manager = manager();
        manager.load(keys.issue(keys.content(LocalDateTime.now().minusDays(10),
            LocalDateTime.now().minusDays(1), 7)));

        assertThat(manager.getStatus()).isEqualTo(LicenseStatus.GRACE);
        // 宽限期内仍可用
        manager.assertUsable();
    }

    @Test
    void illegal_whenExpiredBeyondGrace() {
        LicenseManager manager = manager();
        manager.load(keys.issue(keys.content(LocalDateTime.now().minusDays(30),
            LocalDateTime.now().minusDays(10), 3)));

        assertThat(manager.getStatus()).isEqualTo(LicenseStatus.ILLEGAL);
        assertThatThrownBy(manager::assertUsable).isInstanceOf(LicenseException.class);
    }

    @Test
    void notYetValid_shouldThrowAndBeIllegal() {
        LicenseManager manager = manager();

        assertThatThrownBy(() -> manager.load(keys.issue(keys.content(
            LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(30), 0))))
            .isInstanceOf(LicenseException.class);
        assertThat(manager.getStatus()).isEqualTo(LicenseStatus.ILLEGAL);
    }

    @Test
    void assertModule_shouldRejectUnlicensedModule() {
        LicenseManager manager = manager();
        manager.load(keys.issue(keys.content(LocalDateTime.now().minusDays(1),
            LocalDateTime.now().plusDays(30), 0)));

        manager.assertModule("report");
        assertThatThrownBy(() -> manager.assertModule("unknown-module"))
            .isInstanceOf(LicenseException.class);
    }

    @Test
    void assertQuota_shouldRejectWhenExceeded() {
        LicenseManager manager = manager();
        manager.load(keys.issue(keys.content(LocalDateTime.now().minusDays(1),
            LocalDateTime.now().plusDays(30), 0)));

        manager.assertQuota("device", 100L);
        assertThatThrownBy(() -> manager.assertQuota("device", 101L))
            .isInstanceOf(LicenseException.class);
    }
}

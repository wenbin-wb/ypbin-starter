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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 机器指纹绑定校验单元测试：绑定当前机器可用，绑定其它机器被拒。
 *
 * @author wenbin
 * @since 2026-08-05
 */
class LicenseFingerprintBindingTest {

    private final LicenseTestKeys keys = new LicenseTestKeys();

    private LicenseContent boundTo(List<String> fingerprints) {
        return new LicenseContent("LIC-FP", "指纹测试", "",
            fingerprints, null,
            LocalDateTime.now(), LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30), 0,
            List.of(), Map.of(), Map.of());
    }

    @Test
    void shouldPass_whenBoundToCurrentMachine() {
        String authCode = keys.issue(boundTo(List.of(MachineFingerprint.current())));
        LicenseManager manager = new LicenseManager(keys.sm2.publicKey(), keys.sm4, true);

        manager.load(authCode);

        assertThat(manager.getStatus()).isEqualTo(LicenseStatus.LEGAL);
    }

    @Test
    void shouldReject_whenBoundToOtherMachine() {
        String authCode = keys.issue(boundTo(List.of("0000000000000000000000000000000000000000000000000000000000000000")));
        LicenseManager manager = new LicenseManager(keys.sm2.publicKey(), keys.sm4, true);

        assertThatThrownBy(() -> manager.load(authCode)).isInstanceOf(LicenseException.class);
        assertThat(manager.getStatus()).isEqualTo(LicenseStatus.ILLEGAL);
    }

    @Test
    void shouldIgnoreFingerprint_whenDisabled() {
        String authCode = keys.issue(boundTo(List.of("mismatch-fingerprint")));
        // 关闭指纹校验时即便绑定了其它机器也不拦截（能力开关，非静默降级）
        LicenseManager manager = new LicenseManager(keys.sm2.publicKey(), keys.sm4, false);

        manager.load(authCode);

        assertThat(manager.getStatus()).isEqualTo(LicenseStatus.LEGAL);
    }
}

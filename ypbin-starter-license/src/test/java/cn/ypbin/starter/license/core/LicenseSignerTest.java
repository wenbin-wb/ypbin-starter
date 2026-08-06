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
import cn.ypbin.starter.tools.crypto.Sm2Utils;
import cn.ypbin.starter.tools.crypto.Sm2Utils.KeyPairBase64;
import cn.ypbin.starter.tools.crypto.Sm4Utils;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link LicenseSigner} 签发验签单元测试。
 *
 * @author wenbin
 * @since 2026-08-05
 */
class LicenseSignerTest {

    private final LicenseTestKeys keys = new LicenseTestKeys();

    @Test
    void issueAndVerify_roundTrip() {
        LicenseContent content = keys.content(LocalDateTime.now().minusDays(1),
            LocalDateTime.now().plusDays(30), 7);
        String authCode = keys.issue(content);

        LicenseContent parsed = LicenseSigner.verify(authCode, keys.sm2.publicKey(), keys.sm4);

        assertThat(parsed.licenseId()).isEqualTo("LIC-0001");
        assertThat(parsed.modules()).containsExactly("report", "export");
        assertThat(parsed.quota("device")).isEqualTo(100L);
        assertThat(parsed.attribute("region")).isEqualTo("cn");
    }

    @Test
    void verify_shouldRejectWrongPublicKey() {
        String authCode = keys.issue(keys.content(LocalDateTime.now().minusDays(1),
            LocalDateTime.now().plusDays(30), 0));
        KeyPairBase64 other = Sm2Utils.generateKeyPair();

        assertThatThrownBy(() -> LicenseSigner.verify(authCode, other.publicKey(), keys.sm4))
            .isInstanceOf(LicenseException.class);
    }

    @Test
    void verify_shouldRejectWrongSecretKey() {
        String authCode = keys.issue(keys.content(LocalDateTime.now().minusDays(1),
            LocalDateTime.now().plusDays(30), 0));
        String otherSm4 = Sm4Utils.generateKeyBase64();

        assertThatThrownBy(() -> LicenseSigner.verify(authCode, keys.sm2.publicKey(), otherSm4))
            .isInstanceOf(LicenseException.class);
    }

    @Test
    void verify_shouldRejectCorruptedAuthCode() {
        assertThatThrownBy(() -> LicenseSigner.verify("not-a-valid-base64-envelope",
            keys.sm2.publicKey(), keys.sm4))
            .isInstanceOf(LicenseException.class);
    }

    @Test
    void issue_shouldUseUrlSafeBase64WithoutPadding() {
        String authCode = keys.issue(keys.content(LocalDateTime.now().minusDays(1),
            LocalDateTime.now().plusDays(30), 0));

        assertThat(authCode).doesNotContain("+", "/", "=");
    }

    @Test
    void verify_shouldRejectLegacyV1Envelope() {
        // 手工构造 v1 信封：明文 JSON 载荷直接入信封（无 version 字段），标准 Base64，SM4-GCM 加密
        LicenseContent content = keys.content(LocalDateTime.now().minusDays(1),
            LocalDateTime.now().plusDays(30), 0);
        String payload = LicenseJson.toJson(content);
        String signature = Sm2Utils.sign(payload, keys.sm2.privateKey());
        String envelopeJson = LicenseJson.toJson(
            Map.of("payload", payload, "signature", signature));
        byte[] key = Base64.getDecoder().decode(keys.sm4);
        byte[] encrypted = Sm4Utils.encryptGcm(envelopeJson.getBytes(StandardCharsets.UTF_8), key);
        String legacyAuthCode = Base64.getEncoder().encodeToString(encrypted);

        assertThatThrownBy(() -> LicenseSigner.verify(legacyAuthCode, keys.sm2.publicKey(), keys.sm4))
            .isInstanceOf(LicenseException.class);
    }

    @Test
    void issue_shouldFitUnderMaxLength_forModerateContent() {
        String authCode = keys.issue(keys.content(LocalDateTime.now().minusDays(1),
            LocalDateTime.now().plusDays(30), 7));

        // 中等内容（含 modules/quotas/attributes）应落在 768 上限内，且确实大于简单内容
        assertThat(authCode.length())
            .isGreaterThan(500)
            .isLessThanOrEqualTo(LicenseSigner.MAX_AUTH_CODE_LENGTH);
    }
}

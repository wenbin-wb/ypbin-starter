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
package cn.ypbin.starter.security.password;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.security.password.policy.PasswordExpiration;
import cn.ypbin.starter.security.password.policy.PasswordPolicy;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * {@link PasswordExpiration} 密码有效期判定测试。
 *
 * @author wenbin
 * @since 2026-08-01
 */
class PasswordExpirationTest {

    private PasswordExpiration expiration(int days, int warningDays) {
        PasswordPolicy policy = new PasswordPolicy();
        policy.setExpirationDays(days);
        policy.setExpirationWarningDays(warningDays);
        return new PasswordExpiration(() -> policy);
    }

    @Test
    void neverExpiresWhenDisabled() {
        PasswordExpiration exp = expiration(0, 0);
        assertThat(exp.isExpired(LocalDateTime.now().minusYears(5))).isFalse();
        assertThat(exp.remainingDays(LocalDateTime.now())).isEqualTo(-1);
    }

    @Test
    void expiredAfterDays() {
        PasswordExpiration exp = expiration(90, 7);
        assertThat(exp.isExpired(LocalDateTime.now().minusDays(91))).isTrue();
    }

    @Test
    void notExpiredWithinDays() {
        PasswordExpiration exp = expiration(90, 7);
        assertThat(exp.isExpired(LocalDateTime.now().minusDays(30))).isFalse();
    }

    @Test
    void nullResetTimeTreatedAsExpiredWhenEnabled() {
        PasswordExpiration exp = expiration(90, 7);
        assertThat(exp.isExpired(null)).isTrue();
    }

    @Test
    void shouldWarnWithinWarningWindow() {
        PasswordExpiration exp = expiration(90, 7);
        // 已过 85 天，距 90 天到期剩 5 天，进入 7 天提醒窗口
        assertThat(exp.shouldWarn(LocalDateTime.now().minusDays(85))).isTrue();
    }

    @Test
    void shouldNotWarnOutsideWindow() {
        PasswordExpiration exp = expiration(90, 7);
        assertThat(exp.shouldWarn(LocalDateTime.now().minusDays(30))).isFalse();
    }

    @Test
    void remainingDaysComputed() {
        PasswordExpiration exp = expiration(90, 7);
        long remaining = exp.remainingDays(LocalDateTime.now().minusDays(30));
        assertThat(remaining).isBetween(59L, 60L);
    }
}

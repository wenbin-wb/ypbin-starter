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
package cn.ypbin.starter.security.password.policy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 密码有效期判定工具。
 *
 * <p>纯计算：给定"最后改密时间"与 {@link PasswordPolicyProvider} 提供的有效期策略，判断密码是否已过期、
 * 是否进入到期提醒窗口、剩余天数。starter 只做判定，"过期强制改密"的登录拦截编排由业务侧结合用户表实现
 * （最后改密时间通常存用户表）。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class PasswordExpiration {

    private final PasswordPolicyProvider policyProvider;

    public PasswordExpiration(PasswordPolicyProvider policyProvider) {
        this.policyProvider = policyProvider;
    }

    /**
     * 密码是否已过期。
     *
     * @param lastResetTime 最后改密时间，为空视为从未改密（若启用有效期则按已过期处理）
     * @return 是否过期
     */
    public boolean isExpired(LocalDateTime lastResetTime) {
        PasswordPolicy policy = policyProvider.getPolicy();
        if (!policy.isExpirationEnabled()) {
            return false;
        }
        if (lastResetTime == null) {
            return true;
        }
        LocalDateTime expireAt = lastResetTime.plusDays(policy.getExpirationDays());
        return LocalDateTime.now().isAfter(expireAt);
    }

    /**
     * 是否进入到期提醒窗口（已过提醒起点但尚未过期）。
     *
     * @param lastResetTime 最后改密时间
     * @return 是否应提醒
     */
    public boolean shouldWarn(LocalDateTime lastResetTime) {
        PasswordPolicy policy = policyProvider.getPolicy();
        if (!policy.isExpirationEnabled() || policy.getExpirationWarningDays() <= 0 || lastResetTime == null) {
            return false;
        }
        if (isExpired(lastResetTime)) {
            return false;
        }
        long remaining = remainingDays(lastResetTime);
        return remaining <= policy.getExpirationWarningDays();
    }

    /**
     * 距密码过期的剩余天数。未启用有效期返回 -1；已过期返回 0。
     *
     * @param lastResetTime 最后改密时间
     * @return 剩余天数
     */
    public long remainingDays(LocalDateTime lastResetTime) {
        PasswordPolicy policy = policyProvider.getPolicy();
        if (!policy.isExpirationEnabled()) {
            return -1L;
        }
        if (lastResetTime == null) {
            return 0L;
        }
        LocalDate expireDate = lastResetTime.plusDays(policy.getExpirationDays()).toLocalDate();
        long days = ChronoUnit.DAYS.between(LocalDate.now(), expireDate);
        return Math.max(days, 0L);
    }
}

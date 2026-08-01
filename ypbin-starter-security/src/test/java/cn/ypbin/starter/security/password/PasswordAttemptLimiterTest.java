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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.security.password.lock.AccountLockedException;
import cn.ypbin.starter.security.password.lock.InMemoryPasswordAttemptStore;
import cn.ypbin.starter.security.password.lock.LockStatus;
import cn.ypbin.starter.security.password.lock.PasswordAttemptLimiter;
import cn.ypbin.starter.security.password.policy.PasswordPolicy;
import org.junit.jupiter.api.Test;

/**
 * {@link PasswordAttemptLimiter} 错误锁定测试。
 *
 * @author wenbin
 * @since 2026-08-01
 */
class PasswordAttemptLimiterTest {

    private PasswordAttemptLimiter limiter(PasswordPolicy policy) {
        return new PasswordAttemptLimiter(new InMemoryPasswordAttemptStore(), () -> policy);
    }

    private PasswordPolicy policy(int lockCount, int lockMinutes) {
        PasswordPolicy policy = new PasswordPolicy();
        policy.setErrorLockCount(lockCount);
        policy.setLockMinutes(lockMinutes);
        return policy;
    }

    @Test
    void recordFailureShouldThrowOnReachingThreshold() {
        PasswordAttemptLimiter limiter = limiter(policy(3, 15));

        assertThat(limiter.recordFailure("tom", "1.1.1.1")).isEqualTo(1);
        assertThat(limiter.recordFailure("tom", "1.1.1.1")).isEqualTo(2);
        // 第 3 次达到阈值：本次即抛，避免读写分离竞态
        assertThatThrownBy(() -> limiter.recordFailure("tom", "1.1.1.1"))
            .isInstanceOf(AccountLockedException.class)
            .hasMessageContaining("锁定");
    }

    @Test
    void checkLockedShouldBlockAfterThreshold() {
        PasswordAttemptLimiter limiter = limiter(policy(3, 15));

        limiter.recordFailure("tom", "1.1.1.1");
        limiter.recordFailure("tom", "1.1.1.1");
        assertThatThrownBy(() -> limiter.recordFailure("tom", "1.1.1.1"))
            .isInstanceOf(AccountLockedException.class);

        // 后续登录前 checkLocked 也应被拦
        assertThatThrownBy(() -> limiter.checkLocked("tom", "1.1.1.1"))
            .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void shouldNotLockBelowThreshold() {
        PasswordAttemptLimiter limiter = limiter(policy(3, 15));

        limiter.recordFailure("tom", "1.1.1.1");
        limiter.recordFailure("tom", "1.1.1.1");

        limiter.checkLocked("tom", "1.1.1.1"); // 不抛
        assertThat(limiter.remainingAttempts("tom", "1.1.1.1")).isEqualTo(1);
    }

    @Test
    void resetShouldClearCount() {
        PasswordAttemptLimiter limiter = limiter(policy(3, 15));

        limiter.recordFailure("tom", "1.1.1.1");
        limiter.recordFailure("tom", "1.1.1.1");
        limiter.reset("tom", "1.1.1.1");

        limiter.checkLocked("tom", "1.1.1.1"); // 清除后不再锁
        assertThat(limiter.remainingAttempts("tom", "1.1.1.1")).isEqualTo(3);
    }

    @Test
    void unlockShouldClearAllScopes() {
        PasswordAttemptLimiter limiter = limiter(policy(2, 15));

        // 同一账号从两个 IP 各失败到锁定
        limiter.recordFailure("tom", "1.1.1.1");
        assertThatThrownBy(() -> limiter.recordFailure("tom", "1.1.1.1"))
            .isInstanceOf(AccountLockedException.class);
        limiter.recordFailure("tom", "2.2.2.2");
        assertThatThrownBy(() -> limiter.recordFailure("tom", "2.2.2.2"))
            .isInstanceOf(AccountLockedException.class);

        // 管理员按账号解锁：无需知道被哪些 IP 锁
        limiter.unlock("tom");

        limiter.checkLocked("tom", "1.1.1.1");
        limiter.checkLocked("tom", "2.2.2.2");
    }

    @Test
    void getLockStatusShouldReflectState() {
        PasswordAttemptLimiter limiter = limiter(policy(3, 15));

        limiter.recordFailure("tom", "1.1.1.1");
        LockStatus status = limiter.getLockStatus("tom", "1.1.1.1");
        assertThat(status.locked()).isFalse();
        assertThat(status.failedCount()).isEqualTo(1);
        assertThat(status.remainingAttempts()).isEqualTo(2);

        limiter.recordFailure("tom", "1.1.1.1");
        assertThatThrownBy(() -> limiter.recordFailure("tom", "1.1.1.1"))
            .isInstanceOf(AccountLockedException.class);
        LockStatus locked = limiter.getLockStatus("tom", "1.1.1.1");
        assertThat(locked.locked()).isTrue();
        assertThat(locked.remainingSeconds()).isGreaterThan(0);
    }

    @Test
    void identifierShouldBeCaseInsensitive() {
        PasswordAttemptLimiter limiter = limiter(policy(2, 15));

        limiter.recordFailure("Tom", "1.1.1.1");
        assertThatThrownBy(() -> limiter.recordFailure("tom", "1.1.1.1"))
            .isInstanceOf(AccountLockedException.class);
        // 大小写归一后视为同一账号
        assertThatThrownBy(() -> limiter.checkLocked("TOM", "1.1.1.1"))
            .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void shouldIgnoreWhenLockDisabled() {
        PasswordAttemptLimiter limiter = limiter(policy(0, 15));

        for (int i = 0; i < 10; i++) {
            limiter.recordFailure("tom", "1.1.1.1");
        }

        limiter.checkLocked("tom", "1.1.1.1"); // 锁定关闭，永不抛
        assertThat(limiter.remainingAttempts("tom", "1.1.1.1")).isEqualTo(-1);
    }

    @Test
    void differentScopeCountedSeparately() {
        PasswordAttemptLimiter limiter = limiter(policy(2, 15));

        limiter.recordFailure("tom", "1.1.1.1");
        assertThatThrownBy(() -> limiter.recordFailure("tom", "1.1.1.1"))
            .isInstanceOf(AccountLockedException.class);

        // 另一个 IP 维度独立计数，不受影响
        limiter.checkLocked("tom", "2.2.2.2");
    }
}

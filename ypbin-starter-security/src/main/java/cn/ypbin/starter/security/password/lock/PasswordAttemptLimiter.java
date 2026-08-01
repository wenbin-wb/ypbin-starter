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
package cn.ypbin.starter.security.password.lock;

import cn.ypbin.starter.security.password.policy.PasswordPolicy;
import cn.ypbin.starter.security.password.policy.PasswordPolicyProvider;
import java.time.Duration;
import java.util.Locale;

/**
 * 密码错误锁定限制器。
 *
 * <p>按账号维度记录连续登录失败次数，达到策略阈值即锁定，锁定时长由计数键的过期时间控制、到期自动解锁。
 * 计数与锁定窗口由 {@link PasswordAttemptStore} 存储（Redis/内存），阈值与时长由
 * {@link PasswordPolicyProvider} 提供（每次实时读取，支持后台动态调整）。</p>
 *
 * <p>计数键维度为 {@code 账号:scope}（scope 通常为客户端 IP），账号统一小写归一，避免大小写绕过。
 * 典型用法：</p>
 * <pre>{@code
 * limiter.checkLocked(username, ip);          // 登录前：已锁定则抛 AccountLockedException
 * if (密码错误) {
 *     limiter.recordFailure(username, ip);     // 失败：计数 +1，达阈值则本次即抛锁定
 * } else {
 *     limiter.reset(username, ip);             // 成功：清除计数
 * }
 * }</pre>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class PasswordAttemptLimiter {

    private static final String KEY_PREFIX = "ypbin:security:pwderr:";

    private final PasswordAttemptStore store;
    private final PasswordPolicyProvider policyProvider;

    public PasswordAttemptLimiter(PasswordAttemptStore store, PasswordPolicyProvider policyProvider) {
        this.store = store;
        this.policyProvider = policyProvider;
    }

    /**
     * 校验账号是否处于锁定状态，已锁定则抛出 {@link AccountLockedException}。
     *
     * @param identifier 账号标识（如用户名）
     * @param scope      计数维度补充（如客户端 IP），可空
     */
    public void checkLocked(String identifier, String scope) {
        PasswordPolicy policy = policyProvider.getPolicy();
        if (!policy.isLockEnabled()) {
            return;
        }
        String key = buildKey(identifier, scope);
        long count = store.get(key);
        if (count >= policy.getErrorLockCount()) {
            throwLocked(key);
        }
    }

    /**
     * 记录一次登录失败并递增计数；若本次失败达到阈值，直接抛出 {@link AccountLockedException}。
     *
     * <p>用递增后的返回值判定，避免"读-判-写"分离在并发下的竞态。</p>
     *
     * @param identifier 账号标识
     * @param scope      计数维度补充，可空
     * @return 当前累计失败次数
     */
    public long recordFailure(String identifier, String scope) {
        PasswordPolicy policy = policyProvider.getPolicy();
        if (!policy.isLockEnabled()) {
            return 0L;
        }
        String key = buildKey(identifier, scope);
        Duration lockDuration = Duration.ofMinutes(policy.getLockMinutes());
        // 观察窗口与锁定时长同取 lockMinutes：窗口内累计到阈值即锁定，达阈值时 store 会用满额时长刷新 TTL
        long count = store.increment(key, lockDuration, policy.getErrorLockCount(), lockDuration);
        if (count >= policy.getErrorLockCount()) {
            throwLocked(key);
        }
        return count;
    }

    /**
     * 查询账号锁定状态（供后台展示/前端提示）。
     *
     * @param identifier 账号标识
     * @param scope      计数维度补充，可空
     * @return 锁定状态
     */
    public LockStatus getLockStatus(String identifier, String scope) {
        PasswordPolicy policy = policyProvider.getPolicy();
        if (!policy.isLockEnabled()) {
            return LockStatus.disabled();
        }
        String key = buildKey(identifier, scope);
        long count = store.get(key);
        boolean locked = count >= policy.getErrorLockCount();
        long remainingAttempts = Math.max(policy.getErrorLockCount() - count, 0L);
        long remainingSeconds = locked ? store.getTimeToLiveSeconds(key) : 0L;
        return new LockStatus(locked, count, remainingAttempts, remainingSeconds);
    }

    /**
     * 是否已锁定。
     *
     * @param identifier 账号标识
     * @param scope      计数维度补充，可空
     * @return 是否锁定
     */
    public boolean isLocked(String identifier, String scope) {
        return getLockStatus(identifier, scope).locked();
    }

    /**
     * 剩余可尝试次数（阈值 - 当前失败数），锁定关闭时返回 -1 表示不限制。
     *
     * @param identifier 账号标识
     * @param scope      计数维度补充，可空
     * @return 剩余次数
     */
    public long remainingAttempts(String identifier, String scope) {
        return getLockStatus(identifier, scope).remainingAttempts();
    }

    /**
     * 清除指定维度的失败计数（登录成功或按精确维度解锁时调用）。
     *
     * @param identifier 账号标识
     * @param scope      计数维度补充，可空
     */
    public void reset(String identifier, String scope) {
        store.reset(buildKey(identifier, scope));
    }

    /**
     * 解锁账号的全部维度失败计数（管理员后台解锁：不需知道用户从哪个 IP 被锁）。
     *
     * @param identifier 账号标识
     */
    public void unlock(String identifier) {
        store.resetByPrefix(KEY_PREFIX + normalize(identifier));
    }

    private void throwLocked(String key) {
        long remaining = store.getTimeToLiveSeconds(key);
        throw new AccountLockedException(buildLockedMessage(remaining), remaining);
    }

    private String buildKey(String identifier, String scope) {
        String base = KEY_PREFIX + normalize(identifier);
        return (scope == null || scope.isBlank()) ? base : base + ":" + scope.trim();
    }

    private String normalize(String identifier) {
        return identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT);
    }

    private String buildLockedMessage(long remainingSeconds) {
        long minutes = (remainingSeconds + 59) / 60;
        return "账号已锁定，请 " + Math.max(minutes, 1) + " 分钟后再试";
    }
}

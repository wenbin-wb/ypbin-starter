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
 * <p><strong>双维计数：</strong>除 {@code (账号, scope)}（scope 通常为客户端 IP）键外，还在账号全局
 * 维度维护一个无 scope 的计数键——每次失败两个维度同步 +1。账号维锁定时长更长（单维锁定分钟的
 * 固定倍数），使轮换 IP 无法绕过账号锁定。账号统一小写归一，避免大小写绕过。典型用法：</p>
 * <pre>{@code
 * limiter.checkLocked(username, ip);          // 登录前：已锁定则抛 AccountLockedException
 * if (密码错误) {
 *     limiter.recordFailure(username, ip);     // 失败：两个维度计数 +1，达阈值则本次即抛锁定
 * } else {
 *     limiter.reset(username, ip);             // 成功：清除对应维度与账号维度计数
 * }
 * }</pre>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class PasswordAttemptLimiter {

    private static final String KEY_PREFIX = "ypbin:security:pwderr:";

    /** 账号维度锁定时长相对单维（账号+scope）锁定时长的倍数：IP 轮换者需等更久才能再试 */
    private static final int ACCOUNT_LOCK_MINUTES_MULTIPLIER = 3;

    private final PasswordAttemptStore store;
    private final PasswordPolicyProvider policyProvider;

    public PasswordAttemptLimiter(PasswordAttemptStore store, PasswordPolicyProvider policyProvider) {
        this.store = store;
        this.policyProvider = policyProvider;
    }

    /**
     * 校验账号是否处于锁定状态，已锁定则抛出 {@link AccountLockedException}。
     *
     * <p>先查账号全局维度（换 IP 也锁），再查指定 {@code scope} 维度。</p>
     *
     * @param identifier 账号标识（如用户名）
     * @param scope      计数维度补充（如客户端 IP），可空
     */
    public void checkLocked(String identifier, String scope) {
        PasswordPolicy policy = policyProvider.getPolicy();
        if (!policy.isLockEnabled()) {
            return;
        }
        String accountKey = accountKey(identifier);
        if (store.get(accountKey) >= policy.getErrorLockCount()) {
            throwLocked(accountKey);
        }
        String scopedKey = buildKey(identifier, scope);
        if (!accountKey.equals(scopedKey) && store.get(scopedKey) >= policy.getErrorLockCount()) {
            throwLocked(scopedKey);
        }
    }

    /**
     * 记录一次登录失败并递增计数；若本次失败达到阈值，直接抛出 {@link AccountLockedException}。
     *
     * <p>账号全局维度与 {@code (账号, scope)} 维度同步递增；任一方达到阈值即锁定，账号维锁定
     * 时长更长。用递增后的返回值判定，避免"读-判-写"分离在并发下的竞态。</p>
     *
     * @param identifier 账号标识
     * @param scope      计数维度补充，可空
     * @return 当前 {@code scope} 维度累计失败次数（无 scope 时即账号维度次数）
     */
    public long recordFailure(String identifier, String scope) {
        PasswordPolicy policy = policyProvider.getPolicy();
        if (!policy.isLockEnabled()) {
            return 0L;
        }
        int threshold = policy.getErrorLockCount();
        Duration window = Duration.ofMinutes(policy.getLockMinutes());
        String accountKey = accountKey(identifier);
        // 账号全局维度先递增并判定：防轮换 IP 绕过（锁定时长更长）
        long accountCount = store.increment(accountKey, window, threshold, accountLockDuration(policy));
        if (accountCount >= threshold) {
            throwLocked(accountKey);
        }
        String scopedKey = buildKey(identifier, scope);
        if (accountKey.equals(scopedKey)) {
            // scope 为空时两个维度同一键，避免重复计数
            return accountCount;
        }
        long scopedCount = store.increment(scopedKey, window, threshold, window);
        if (scopedCount >= threshold) {
            throwLocked(scopedKey);
        }
        return scopedCount;
    }

    /**
     * 查询账号锁定状态（供后台展示/前端提示）。
     *
     * <p>账号全局维度与 {@code (账号, scope)} 维度取高者判定（与 {@link #checkLocked} 执行语义一致）：
     * 任一维度达到阈值即视为锁定，返回计数取两维较大值，避免"账号维已锁但后台显示未锁"的矛盾。</p>
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
        int threshold = policy.getErrorLockCount();
        String accountKey = accountKey(identifier);
        long accountCount = store.get(accountKey);
        String scopedKey = buildKey(identifier, scope);
        // scope 为空时两个维度同一键，避免重复计数
        long scopedCount = accountKey.equals(scopedKey) ? accountCount : store.get(scopedKey);
        // 账号全局维度优先：与 checkLocked 执行语义一致（账号维达阈值即锁定，即使本 scope 未达）
        long effectiveCount = Math.max(accountCount, scopedCount);
        boolean locked = effectiveCount >= threshold;
        long remainingAttempts = Math.max((long) threshold - effectiveCount, 0L);
        long remainingSeconds = locked
            ? Math.max(store.getTimeToLiveSeconds(accountKey), store.getTimeToLiveSeconds(scopedKey))
            : 0L;
        return new LockStatus(locked, effectiveCount, remainingAttempts, remainingSeconds);
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
     * 清除指定维度的失败计数（登录成功或按精确维度解锁时调用）；同时清除账号全局维度计数。
     *
     * @param identifier 账号标识
     * @param scope      计数维度补充，可空
     */
    public void reset(String identifier, String scope) {
        String accountKey = accountKey(identifier);
        String scopedKey = buildKey(identifier, scope);
        store.reset(scopedKey);
        if (!accountKey.equals(scopedKey)) {
            store.reset(accountKey);
        }
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

    /** 账号全局维度计数键（无 scope） */
    private String accountKey(String identifier) {
        return KEY_PREFIX + normalize(identifier);
    }

    /** 账号维度锁定满额时长：单维锁定时长的 {@link #ACCOUNT_LOCK_MINUTES_MULTIPLIER} 倍 */
    private Duration accountLockDuration(PasswordPolicy policy) {
        return Duration.ofMinutes((long) policy.getLockMinutes() * ACCOUNT_LOCK_MINUTES_MULTIPLIER);
    }

    private String buildKey(String identifier, String scope) {
        String base = accountKey(identifier);
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

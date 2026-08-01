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

import java.io.Serial;
import java.io.Serializable;

/**
 * 密码安全策略。
 *
 * <p>描述密码复杂度、错误锁定、有效期等策略值。starter 只定义运行时模型与校验逻辑，策略值来源由
 * {@link PasswordPolicyProvider} 决定：可来自配置文件，也可由业务系统从数据库（如配置中心表）读取，
 * 从而支持后台可视化调整。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class PasswordPolicy implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 最小长度 */
    private int minLength = 8;

    /** 最大长度 */
    private int maxLength = 32;

    /** 是否必须包含数字 */
    private boolean requireDigit = true;

    /** 是否必须包含字母 */
    private boolean requireLetter = true;

    /** 是否必须包含大写字母 */
    private boolean requireUppercase = false;

    /** 是否必须包含小写字母 */
    private boolean requireLowercase = false;

    /** 是否必须包含特殊字符 */
    private boolean requireSymbol = false;

    /** 是否允许密码包含用户名（含反序） */
    private boolean allowContainUsername = false;

    /** 登录错误锁定阈值，0 表示不锁定 */
    private int errorLockCount = 5;

    /** 账号锁定时长（分钟） */
    private int lockMinutes = 15;

    /** 密码有效期（天），0 表示永不过期 */
    private int expirationDays = 0;

    /** 密码到期提醒天数，0 表示不提醒 */
    private int expirationWarningDays = 0;

    /** 历史密码不可重复次数，0 表示不校验历史密码 */
    private int historyCount = 0;

    /**
     * 是否启用错误锁定。
     *
     * @return true 启用
     */
    public boolean isLockEnabled() {
        return errorLockCount > 0;
    }

    /**
     * 是否启用密码有效期。
     *
     * @return true 启用
     */
    public boolean isExpirationEnabled() {
        return expirationDays > 0;
    }

    public int getMinLength() {
        return minLength;
    }

    public void setMinLength(int minLength) {
        this.minLength = minLength;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public boolean isRequireDigit() {
        return requireDigit;
    }

    public void setRequireDigit(boolean requireDigit) {
        this.requireDigit = requireDigit;
    }

    public boolean isRequireLetter() {
        return requireLetter;
    }

    public void setRequireLetter(boolean requireLetter) {
        this.requireLetter = requireLetter;
    }

    public boolean isRequireUppercase() {
        return requireUppercase;
    }

    public void setRequireUppercase(boolean requireUppercase) {
        this.requireUppercase = requireUppercase;
    }

    public boolean isRequireLowercase() {
        return requireLowercase;
    }

    public void setRequireLowercase(boolean requireLowercase) {
        this.requireLowercase = requireLowercase;
    }

    public boolean isRequireSymbol() {
        return requireSymbol;
    }

    public void setRequireSymbol(boolean requireSymbol) {
        this.requireSymbol = requireSymbol;
    }

    public boolean isAllowContainUsername() {
        return allowContainUsername;
    }

    public void setAllowContainUsername(boolean allowContainUsername) {
        this.allowContainUsername = allowContainUsername;
    }

    public int getErrorLockCount() {
        return errorLockCount;
    }

    public void setErrorLockCount(int errorLockCount) {
        this.errorLockCount = errorLockCount;
    }

    public int getLockMinutes() {
        return lockMinutes;
    }

    public void setLockMinutes(int lockMinutes) {
        this.lockMinutes = lockMinutes;
    }

    public int getExpirationDays() {
        return expirationDays;
    }

    public void setExpirationDays(int expirationDays) {
        this.expirationDays = expirationDays;
    }

    public int getExpirationWarningDays() {
        return expirationWarningDays;
    }

    public void setExpirationWarningDays(int expirationWarningDays) {
        this.expirationWarningDays = expirationWarningDays;
    }

    public int getHistoryCount() {
        return historyCount;
    }

    public void setHistoryCount(int historyCount) {
        this.historyCount = historyCount;
    }
}

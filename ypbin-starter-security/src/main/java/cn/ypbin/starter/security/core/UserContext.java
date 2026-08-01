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
package cn.ypbin.starter.security.core;

import cn.dev33.satoken.stp.StpUtil;
import java.util.Optional;

/**
 * 当前登录用户上下文门面。
 *
 * <p>在 {@link LoginHelper} 只提供用户 ID 的基础上，进一步提供用户名、租户、扩展属性等常用信息，
 * 供业务在任意层静态读取当前登录人，免去到处写 {@code StpUtil} 与手动取会话。</p>
 *
 * <p>登录用户信息来自登录会话（Sa-Token Session）：登录成功后由业务方通过 {@link #setLoginUser} 存入
 * {@link LoginUser}，本类据此读取用户名、租户等；starter 不假设具体用户模型，业务自有字段用
 * {@link #setAttribute}/{@link #getAttribute} 另存自取。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public final class UserContext {

    /** 会话中存登录用户信息的键 */
    public static final String KEY_LOGIN_USER = "ypbin:loginUser";

    private UserContext() {
    }

    /**
     * 当前登录用户 ID。
     *
     * @return 用户 ID
     */
    public static Long getUserId() {
        return LoginHelper.getUserId();
    }

    /**
     * 安全获取当前登录用户 ID（未登录不抛异常）。
     *
     * @return 用户 ID 的 Optional
     */
    public static Optional<Long> getUserIdSafely() {
        return LoginHelper.getUserIdSafely();
    }

    /**
     * 是否已登录。
     *
     * @return 登录状态
     */
    public static boolean isLogin() {
        return LoginHelper.isLogin();
    }

    /**
     * 获取当前登录用户完整信息。登录时未写入则为空。
     *
     * @return {@link LoginUser} 的 Optional
     */
    public static Optional<LoginUser> getLoginUser() {
        return getAttribute(KEY_LOGIN_USER, LoginUser.class);
    }

    /**
     * 登录后写入完整登录用户信息到会话。
     *
     * @param loginUser 登录用户信息
     */
    public static void setLoginUser(LoginUser loginUser) {
        setAttribute(KEY_LOGIN_USER, loginUser);
    }

    /**
     * 当前登录用户名。未写入 {@link LoginUser} 则为空。
     *
     * @return 用户名的 Optional
     */
    public static Optional<String> getUsername() {
        return getLoginUser().map(LoginUser::getUsername);
    }

    /**
     * 当前登录用户所属租户 ID。未写入 {@link LoginUser} 则为空。
     *
     * @return 租户 ID 的 Optional
     */
    public static Optional<Long> getTenantId() {
        return getLoginUser().map(LoginUser::getTenantId);
    }

    /**
     * 当前登录客户端 ID。未写入 {@link LoginUser} 则为空。
     *
     * @return 客户端 ID 的 Optional
     */
    public static Optional<String> getClientId() {
        return getLoginUser().map(LoginUser::getClientId);
    }

    /**
     * 当前登录客户端类型。未写入 {@link LoginUser} 则为空。
     *
     * @return 客户端类型的 Optional
     */
    public static Optional<String> getClientType() {
        return getLoginUser().map(LoginUser::getClientType);
    }

    /**
     * 当前登录认证方式。未写入 {@link LoginUser} 则为空。
     *
     * @return 认证方式的 Optional
     */
    public static Optional<String> getAuthType() {
        return getLoginUser().map(LoginUser::getAuthType);
    }

    /**
     * 从当前会话读取扩展属性。
     *
     * @param key  属性键
     * @param type 期望类型
     * @param <T>  泛型
     * @return 属性值的 Optional；未登录或不存在时为空
     */
    @SuppressWarnings("unchecked")
    public static <T> Optional<T> getAttribute(String key, Class<T> type) {
        if (!StpUtil.isLogin()) {
            return Optional.empty();
        }
        Object value = StpUtil.getSession().get(key);
        return value == null ? Optional.empty() : Optional.of((T) value);
    }

    /**
     * 向当前会话写入扩展属性（需已登录）。
     *
     * @param key   属性键
     * @param value 属性值
     */
    public static void setAttribute(String key, Object value) {
        StpUtil.getSession().set(key, value);
    }
}

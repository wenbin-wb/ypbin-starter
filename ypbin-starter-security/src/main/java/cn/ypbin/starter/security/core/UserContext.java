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
 * <p>用户名、租户 ID 等业务属性来自登录会话（Sa-Token Session）：登录成功后由业务方通过
 * {@link #setUsername}/{@link #setTenantId}/{@link #setAttribute} 写入，本类据此读取。starter 不假设
 * 具体用户模型，扩展字段用 {@link #getAttribute} 自取。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public final class UserContext {

    /** 会话中存用户名的键 */
    public static final String KEY_USERNAME = "ypbin:username";

    /** 会话中存租户 ID 的键 */
    public static final String KEY_TENANT_ID = "ypbin:tenantId";

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
     * 当前登录用户名。登录时未写入则为空。
     *
     * @return 用户名的 Optional
     */
    public static Optional<String> getUsername() {
        return getAttribute(KEY_USERNAME, String.class);
    }

    /**
     * 登录后写入用户名到会话。
     *
     * @param username 用户名
     */
    public static void setUsername(String username) {
        setAttribute(KEY_USERNAME, username);
    }

    /**
     * 当前登录用户所属租户 ID。登录时未写入则为空。
     *
     * @return 租户 ID 的 Optional
     */
    public static Optional<Long> getTenantId() {
        return getAttribute(KEY_TENANT_ID, Object.class).map(v -> Long.valueOf(v.toString()));
    }

    /**
     * 登录后写入租户 ID 到会话。
     *
     * @param tenantId 租户 ID
     */
    public static void setTenantId(Long tenantId) {
        setAttribute(KEY_TENANT_ID, tenantId);
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

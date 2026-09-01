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
package cn.ypbin.starter.security.identity;

import cn.ypbin.starter.security.core.LoginUser;
import java.util.Optional;

/**
 * 当前登录用户上下文（微服务身份头模式）。
 *
 * <p>网关校验 token 后签发 {@code X-User-Id} 等可信身份头，{@link IdentityHeaderFilter}
 * 据此构建 {@link LoginUser} 写入本上下文；业务代码读取当前用户统一走本类。</p>
 *
 * <p>与单体版 {@code cn.ypbin.starter.security.core.UserContext}（绑定 sa-token 会话）
 * 职责对等但实现无关：微服务版依赖网关签发的可信头，不依赖 sa-token 会话。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
public final class IdentityContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private IdentityContext() {
    }

    /**
     * 写入当前登录用户（由 {@link IdentityHeaderFilter} 调用）。
     */
    public static void setLoginUser(LoginUser loginUser) {
        HOLDER.set(loginUser);
    }

    /**
     * 当前登录用户，未登录时为空。
     */
    public static Optional<LoginUser> getLoginUser() {
        return Optional.ofNullable(HOLDER.get());
    }

    /**
     * 当前登录用户 ID，未登录时为空。
     */
    public static Optional<Long> getUserId() {
        LoginUser user = HOLDER.get();
        return user == null || user.getId() == null
            ? Optional.empty()
            : Optional.of(user.getId());
    }

    /**
     * 当前登录用户名，未登录时为空。
     */
    public static Optional<String> getUsername() {
        LoginUser user = HOLDER.get();
        return user == null || user.getUsername() == null
            ? Optional.empty()
            : Optional.of(user.getUsername());
    }

    /**
     * 当前租户 ID，未登录或未指定时为空。
     */
    public static Optional<Long> getTenantId() {
        LoginUser user = HOLDER.get();
        return user == null || user.getTenantId() == null
            ? Optional.empty()
            : Optional.of(user.getTenantId());
    }

    /**
     * 是否已登录（存在身份头）。
     */
    public static boolean isLogin() {
        return HOLDER.get() != null;
    }

    /**
     * 清理当前线程上下文（由 {@link IdentityHeaderFilter} 在请求结束调用）。
     */
    public static void clear() {
        HOLDER.remove();
    }
}

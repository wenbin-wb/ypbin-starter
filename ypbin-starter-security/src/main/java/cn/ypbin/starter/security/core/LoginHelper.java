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
 * 登录辅助工具。
 *
 * <p>对 Sa-Token 常用操作做轻量封装，统一以 {@code Long} 类型的用户 ID 进出，
 * 让业务代码保持类型一致，避免散落的 {@code StpUtil} 调用与手动类型转换。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public final class LoginHelper {

    private LoginHelper() {
    }

    /**
     * 执行登录。
     *
     * @param userId 用户 ID
     */
    public static void login(Long userId) {
        StpUtil.login(userId);
    }

    /**
     * 执行登录并指定设备。
     *
     * @param userId 用户 ID
     * @param device 设备标识
     */
    public static void login(Long userId, String device) {
        StpUtil.login(userId, device);
    }

    /**
     * 当前会话登出。
     */
    public static void logout() {
        StpUtil.logout();
    }

    /**
     * 踢指定用户下线。
     *
     * @param userId 用户 ID
     */
    public static void kickout(Long userId) {
        StpUtil.kickout(userId);
    }

    /**
     * 是否已登录。
     *
     * @return 登录状态
     */
    public static boolean isLogin() {
        return StpUtil.isLogin();
    }

    /**
     * 获取当前登录用户 ID。
     *
     * @return 用户 ID
     */
    public static Long getUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    /**
     * 安全获取当前登录用户 ID（未登录不抛异常）。
     *
     * @return 用户 ID 的 Optional
     */
    public static Optional<Long> getUserIdSafely() {
        Object loginId = StpUtil.getLoginIdDefaultNull();
        if (loginId == null) {
            return Optional.empty();
        }
        return Optional.of(Long.valueOf(loginId.toString()));
    }

    /**
     * 获取当前会话 token 值。
     *
     * @return token
     */
    public static String getTokenValue() {
        return StpUtil.getTokenValue();
    }
}

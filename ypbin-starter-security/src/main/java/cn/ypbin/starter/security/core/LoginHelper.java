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

import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.ypbin.starter.security.client.LoginClient;
import cn.ypbin.starter.security.client.LoginClientHolder;
import cn.ypbin.starter.security.client.LoginClientRequest;
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
     * 按客户端策略执行登录。
     *
     * <p>方法会读取 {@link cn.ypbin.starter.security.client.LoginClientProvider} 提供的客户端配置，
     * 按客户端独立的 token 有效期、活跃超时、多端并发、最大登录数等策略登录。</p>
     *
     * @param userId  用户 ID
     * @param request 客户端登录请求
     * @return 命中的客户端配置，业务可写入 {@link LoginUser}
     */
    public static LoginClient login(Long userId, LoginClientRequest request) {
        return LoginClientHolder.getService().login(userId, request);
    }

    /**
     * 按客户端策略执行登录。
     *
     * @param userId   用户 ID
     * @param clientId 客户端 ID
     * @param authType 认证方式
     * @return 命中的客户端配置，业务可写入 {@link LoginUser}
     */
    public static LoginClient login(Long userId, String clientId, String authType) {
        return login(userId, new LoginClientRequest(clientId, authType));
    }

    /**
     * 按客户端策略执行登录并指定设备 ID。
     *
     * @param userId   用户 ID
     * @param clientId 客户端 ID
     * @param authType 认证方式
     * @param deviceId 设备 ID
     * @return 命中的客户端配置，业务可写入 {@link LoginUser}
     */
    public static LoginClient login(Long userId, String clientId, String authType, String deviceId) {
        LoginClientRequest request = new LoginClientRequest(clientId, authType);
        request.setDeviceId(deviceId);
        return login(userId, request);
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
        try {
            return StpUtil.isLogin();
        } catch (SaTokenException e) {
            // 无 Sa-Token 上下文（异步线程、定时任务等）：视为未登录，不抛异常
            return false;
        }
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
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            if (loginId == null) {
                return Optional.empty();
            }
            return Optional.of(Long.valueOf(loginId.toString()));
        } catch (SaTokenException e) {
            // 无 Sa-Token 上下文（异步线程、定时任务等）：视为无当前登录人，不抛异常。
            // 关键场景：审计填充 AuditorProvider 走本方法，异步落库时须安全跳过而非崩溃。
            return Optional.empty();
        }
    }

    /**
     * 获取当前会话 token 值。
     *
     * @return token
     */
    public static String getTokenValue() {
        return StpUtil.getTokenValue();
    }

    // ------------------------------------------------------------------ 续期 / 超时
    //
    // 说明：Sa-Token 是「续期」机制而非 OAuth2 双令牌（access + refresh）。分两层超时：
    //   - timeout（固定有效期，到点必过期）
    //   - activeTimeout（活跃超时，一段时间无操作则冻结；持续操作可自动续活）
    // 开启 sa-token.active-timeout 并配合 auto-renew 可让活跃用户的 token 自动续期，
    // 通常无需手动调用；下列方法用于需要显式控制续期/查询剩余时长的场景。

    /**
     * 获取当前 token 的完整信息（含 token 值、登录 ID、剩余有效期等）。
     *
     * @return {@link SaTokenInfo}
     */
    public static SaTokenInfo getTokenInfo() {
        return StpUtil.getTokenInfo();
    }

    /**
     * 获取当前 token 剩余有效期（秒）。
     *
     * @return 剩余秒数；{@code -1} 永不过期，{@code -2} 不存在/已过期
     */
    public static long getTokenTimeout() {
        return StpUtil.getTokenTimeout();
    }

    /**
     * 获取当前 token 剩余活跃有效期（秒），即距离被「冻结」还剩多久。
     *
     * @return 剩余秒数；{@code -1} 表示未启用活跃超时
     */
    public static long getTokenActiveTimeout() {
        return StpUtil.getTokenActiveTimeout();
    }

    /**
     * 手动续期：重设当前 token 的固定有效期（timeout）。
     *
     * @param timeout 新的有效期（秒）
     */
    public static void renewTimeout(long timeout) {
        StpUtil.renewTimeout(timeout);
    }

    /**
     * 续活跃：把当前 token 的最后活跃时间刷新为当前时刻，避免因活跃超时被冻结。
     */
    public static void updateLastActiveToNow() {
        StpUtil.updateLastActiveToNow();
    }
}

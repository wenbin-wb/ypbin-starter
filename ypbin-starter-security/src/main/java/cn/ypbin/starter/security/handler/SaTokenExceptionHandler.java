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
package cn.ypbin.starter.security.handler;

import cn.dev33.satoken.exception.DisableServiceException;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.NotSafeException;
import cn.dev33.satoken.exception.SaTokenException;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Sa-Token 认证/鉴权异常处理器。
 *
 * <p>把 Sa-Token 抛出的认证鉴权异常转换为统一 {@link R} 响应，避免落入 web 模块 GlobalExceptionHandler
 * 的兜底而错误地返回 500。分层考虑：认证异常仅 security 模块认识 Sa-Token，故处理器放在此处，web 模块
 * 不反向依赖 sa-token。</p>
 *
 * <p>与 web 全局处理器共存：Spring 按异常类型精确度匹配 {@code @ExceptionHandler}，本处理器处理更具体的
 * Sa-Token 异常，web 兜底 {@code Exception}，互不冲突。标注更高优先级以确保在多 advice 场景优先匹配。
 * 业务方提供自定义同类处理器可覆盖（本 Bean 用 {@code @ConditionalOnMissingBean} 装配）。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SaTokenExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SaTokenExceptionHandler.class);

    /**
     * 未登录 / 登录态失效（未提供 token、token 无效、已过期、被顶下线、被踢下线等）。
     */
    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLogin(NotLoginException e) {
        log.warn("[未认证] type={}, msg={}", e.getType(), e.getMessage());
        return R.fail(GlobalErrorCode.UNAUTHORIZED.getCode(), resolveNotLoginMessage(e));
    }

    /**
     * 无权限。
     */
    @ExceptionHandler(NotPermissionException.class)
    public R<Void> handleNotPermission(NotPermissionException e) {
        log.warn("[无权限] permission={}", e.getPermission());
        return R.fail(GlobalErrorCode.FORBIDDEN.getCode(), GlobalErrorCode.FORBIDDEN.getMessage());
    }

    /**
     * 无角色。
     */
    @ExceptionHandler(NotRoleException.class)
    public R<Void> handleNotRole(NotRoleException e) {
        log.warn("[无角色] role={}", e.getRole());
        return R.fail(GlobalErrorCode.FORBIDDEN.getCode(), GlobalErrorCode.FORBIDDEN.getMessage());
    }

    /**
     * 账号被封禁。
     */
    @ExceptionHandler(DisableServiceException.class)
    public R<Void> handleDisableService(DisableServiceException e) {
        log.warn("[账号被封] service={}, level={}, disableTime={}", e.getService(), e.getLevel(), e.getDisableTime());
        return R.fail(GlobalErrorCode.FORBIDDEN.getCode(), "账号已被封禁，请稍后再试");
    }

    /**
     * 二级认证未通过（如 {@code @SaCheckSafe}）。
     *
     * <p>无状态身份模式（{@code cn.ypbin.starter.security.identity.IdentityStpLogic}）下没有安全会话，
     * 该注解一律拒绝——方向是「拒绝」而不是放行，这里给出明确的 403 文案而不是落到兜底 500。</p>
     */
    @ExceptionHandler(NotSafeException.class)
    public R<Void> handleNotSafe(NotSafeException e) {
        log.warn("[二级认证未通过] {}", e.getMessage());
        return R.fail(GlobalErrorCode.FORBIDDEN.getCode(), "需要完成二级认证");
    }

    /**
     * 其它 Sa-Token 异常兜底。
     *
     * <p>例如无状态身份模式下 {@code renewTimeout} 抛出的「未能查询到对应终端信息，无法续期」——
     * 这类异常表达的是「当前鉴权模式下该能力不可用」。若不在此兜底，它会落到 web 模块
     * {@code GlobalExceptionHandler} 的 {@code Exception} 分支被当成 500，把能力边界误报成服务端故障。</p>
     */
    @ExceptionHandler(SaTokenException.class)
    public R<Void> handleSaTokenException(SaTokenException e) {
        log.warn("[鉴权异常] {}: {}", e.getClass().getSimpleName(), e.getMessage());
        return R.fail(GlobalErrorCode.FORBIDDEN.getCode(), "当前鉴权模式下该操作不可用");
    }

    private String resolveNotLoginMessage(NotLoginException e) {
        // Sa-Token 各类未登录场景统一给前端友好文案，具体 type 已记日志便于排查
        return switch (e.getType()) {
            case NotLoginException.KICK_OUT -> "您已被强制下线";
            case NotLoginException.BE_REPLACED -> "您的账号已在别处登录";
            case NotLoginException.TOKEN_TIMEOUT -> "登录已过期，请重新登录";
            default -> "登录状态无效，请重新登录";
        };
    }
}

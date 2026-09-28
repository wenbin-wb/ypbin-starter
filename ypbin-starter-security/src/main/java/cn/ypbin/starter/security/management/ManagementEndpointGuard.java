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
package cn.ypbin.starter.security.management;

import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.core.util.LogSanitizer;
import cn.ypbin.starter.security.core.PermissionProvider;
import cn.ypbin.starter.security.core.UserContext;
import cn.ypbin.starter.security.satoken.StpPermissionAdapter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 管理端点权限守卫（servlet 下游专用）。
 *
 * <p><strong>解决的问题（反馈 UP-5）</strong>：{@code /actuator/**} 默认只校登录、无权限码，
 * 任何已登录用户都能读平台级指标；本守卫对管理端点基路径（默认 {@code /actuator}）下的端点做
 * <b>权限码</b>收口，把"登录可读"变成"有权限才可读"。</p>
 *
 * <p><strong>放行规则</strong>（顺序判定，任一命中即放行）：</p>
 * <ul>
 *     <li>非管理端点路径（不在 {@code basePath} 下）→ 放行（守卫只关心管理端点）；</li>
 *     <li>公开端点（{@code publicPaths}，默认 {@code /health}、{@code /info}）→ 放行
 *     （健康探针与基本信息是有意公开的，不得误伤可用性）；</li>
 *     <li>其余管理端点：当前用户已登录<strong>且</strong>权限包含 {@code required-permission}
 *     （平台超管约定 {@code *:*:*} 亦放行）→ 放行。</li>
 * </ul>
 *
 * <p><strong>fail-closed</strong>：未配置 {@code required-permission}、未登录、或权限不足
 * 一律拒绝（业务码 403），绝不因"无法判定"而放行。权限数据复用 {@code PermissionProvider}
 * 扩展点（与注解鉴权同一来源），loginType 不参与判定（宿主实现按 loginId 提供权限码）。</p>
 *
 * <p><strong>装配前提</strong>：需要在当前请求能解析出用户的链路中工作——微服务模式在
 * {@code IdentityHeaderFilter} 之后（身份头 → {@link IdentityContext}），单体模式在 Sa-Token
 * 会话就绪后；通过 {@code UserContext} 双形态取值与具体模式解耦。</p>
 *
 * @author wenbin
 * @since 2026-09-28
 */
public class ManagementEndpointGuard extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ManagementEndpointGuard.class);

    /** 拒绝响应兜底报文（ObjectMapper 不可用/序列化失败时使用，遵循 R 结构 + HTTP 200 约定） */
    private static final String REJECTED_FALLBACK_JSON =
        "{\"code\":403,\"message\":\"没有访问权限\",\"data\":null,\"success\":false}";

    private static final String REJECTED_MESSAGE = "管理端点需要管理权限";

    private final ManagementEndpointProperties properties;

    private final PermissionProvider permissionProvider;

    /** 拒绝响应的序列化器（可为 {@code null}，此时回退固定报文） */
    @Nullable
    private final ObjectMapper objectMapper;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public ManagementEndpointGuard(ManagementEndpointProperties properties,
            PermissionProvider permissionProvider, @Nullable ObjectMapper objectMapper) {
        this.properties = properties;
        this.permissionProvider = permissionProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
        String path = servletPath(request);
        if (!isManagementPath(path) || isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!hasRequiredPermission()) {
            log.warn("[ypbin-starter] 拒绝访问管理端点 {}：未登录或权限不足（fail-closed）",
                LogSanitizer.sanitize(path));
            writeForbidden(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 取去除 contextPath 后的请求路径（守卫按部署路径匹配，与应用挂在根路径或子路径无关）。
     *
     * @param request 当前请求
     * @return 相对 contextPath 的路径（不以 contextPath 开头时原样返回）
     */
    private static String servletPath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String uri = request.getRequestURI();
        if (StringUtils.hasText(contextPath) && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    /**
     * 请求路径是否落在管理端点基路径下。
     *
     * @param path 去除 contextPath 后的路径
     * @return 是管理端点路径返回 {@code true}
     */
    private boolean isManagementPath(String path) {
        String base = normalize(properties.getBasePath());
        return path.equals(base) || path.startsWith(base + "/");
    }

    /**
     * 是否公开端点（放行，不校验权限）。
     *
     * @param path 去除 contextPath 后的路径
     * @return 命中公开端点返回 {@code true}
     */
    private boolean isPublicPath(String path) {
        String base = normalize(properties.getBasePath());
        for (String publicPath : properties.getPublicPaths()) {
            String pattern = base + normalize(publicPath);
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判定当前用户是否具备管理权限（fail-closed）。
     *
     * <p>未配置 {@code required-permission}、未登录（拿不到 userId）、权限数据缺失、
     * 或权限集合不含目标权限码/超管约定，均返回 {@code false} 由调用方拒绝。</p>
     *
     * @return 具备管理权限返回 {@code true}
     */
    private boolean hasRequiredPermission() {
        String required = properties.getRequiredPermission();
        if (!StringUtils.hasText(required)) {
            // 未配置目标权限码：无法判定权限，按拒绝处理（fail-closed）
            log.warn("[ypbin-starter] 未配置 ypbin.security.management.required-permission，管理端点一律拒绝（fail-closed）");
            return false;
        }
        Long userId = UserContext.getUserId();
        if (userId == null) {
            // 未登录：直接拒绝，不把 null 交给权限数据源
            return false;
        }
        List<String> permissions = permissionProvider.getPermissions(userId, "");
        if (permissions == null) {
            return false;
        }
        return permissions.contains(required)
            || permissions.contains(StpPermissionAdapter.SUPER_ADMIN)
            || permissions.contains(StpPermissionAdapter.ANY);
    }

    /**
     * 写拒绝响应（遵循全局约定：HTTP 200 + 业务码，R 结构）。
     */
    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        if (objectMapper == null) {
            response.getWriter().write(REJECTED_FALLBACK_JSON);
            return;
        }
        try {
            objectMapper.writeValue(response.getWriter(),
                R.fail(GlobalErrorCode.FORBIDDEN.getCode(), REJECTED_MESSAGE));
        } catch (JacksonException e) {
            log.warn("[ypbin-starter] 管理端点拒绝响应序列化失败，回退固定报文", e);
            response.getWriter().write(REJECTED_FALLBACK_JSON);
        }
    }

    /**
     * 路径归一化：确保以 {@code /} 开头。
     *
     * @param path 原始路径（可为空）
     * @return 归一化路径，空输入返回 {@code /}
     */
    private static String normalize(@Nullable String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}

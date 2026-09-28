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

import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.core.util.LogSanitizer;
import cn.ypbin.starter.security.core.LoginUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 内部身份头过滤器（微服务下游专用）。
 *
 * <p>网关校验 token 后签发 {@code X-User-Id/X-User-Name/X-Tenant-Id/X-Dept-Id/X-Roles}
 * 可信身份头，并同时写出来源标记（{@code X-Gateway-Signed}）。各业务服务装配本过滤器，
 * 从这些头构建 {@link LoginUser} 写入 {@link IdentityContext}，供业务代码无感知读取
 * 当前用户——服务自身不再校验 token。</p>
 *
 * <p><strong>来源校验（SF-5）</strong>：本过滤器不再无条件信任身份头。请求携带身份头时，
 * 必须同时携带与配置一致的可信来源标记（{@code trustedSourceToken}），缺失或不匹配
 * 一律 <strong>拒绝请求</strong>（fail-closed）——防止直连下游服务端口 + 构造身份头
 * 绕过网关鉴权伪造任意用户身份。未配置 {@code trustedSourceToken} 时本过滤器拒绝一切
 * 身份头（安全默认；正常装配路径下该状态由 {@link IdentityAutoConfiguration} 以启动失败
 * 拦截，无参构造仅供单元测试与降级兜底）。</p>
 *
 * <p><strong>默认关闭</strong>：本过滤器仅在 {@code ypbin.security.identity.enabled=true}
 * 时由 {@link IdentityAutoConfiguration} 装配。仅当服务位于可信网关之后、且网关负责清洗
 * 外部 {@code X-User-Id/X-Roles} 等头并签发内部身份头时才应显式开启。</p>
 *
 * <p><strong>畸形头容错：</strong>{@code Long} 型头（userId/tenantId/deptId）解析失败按"无该字段"
 * 处理并记 debug，不中断请求；其中 userId 是身份锚点，userId 解析失败时整体视同无有效身份头
 * （不构建登录用户），避免以空 id 的残缺身份误导下游鉴权。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
public class IdentityHeaderFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdentityHeaderFilter.class);

    /** 拒绝响应兜底报文（ObjectMapper 不可用/序列化失败时使用，遵循 R 结构 + HTTP 200 约定） */
    private static final String REJECTED_FALLBACK_JSON =
        "{\"code\":403,\"message\":\"非法身份来源\",\"data\":null,\"success\":false}";

    private static final String REJECTED_MESSAGE = "非法身份来源：身份头缺少可信网关签名";

    /** 身份头来源标记头名（由可信网关签发） */
    private final String trustedSourceHeader;

    /** 身份头来源标记期望值；为 {@code null} 表示不信任任何来源（fail-closed） */
    @Nullable
    private final String trustedSourceToken;

    /** 拒绝响应的序列化器（可为 {@code null}，此时回退固定报文） */
    @Nullable
    private final ObjectMapper objectMapper;

    /**
     * 以显式来源标记构造（正常装配路径）。
     *
     * @param trustedSourceHeader 来源标记头名（默认 {@value IdentityHeaders#GATEWAY_SIGNED}）
     * @param trustedSourceToken  来源标记期望值（与网关配置一致；为 {@code null} 表示不信任任何来源）
     * @param objectMapper        拒绝响应的序列化器，可为 {@code null}
     */
    public IdentityHeaderFilter(String trustedSourceHeader, @Nullable String trustedSourceToken,
            @Nullable ObjectMapper objectMapper) {
        this.trustedSourceHeader = trustedSourceHeader;
        this.trustedSourceToken = trustedSourceToken;
        this.objectMapper = objectMapper;
    }

    /**
     * 安全默认构造：未提供来源标记 —— 拒绝一切携带身份头的请求（fail-closed）。
     * 仅供单元测试与降级场景；正常装配由 {@link IdentityAutoConfiguration} 保证来源标记存在。
     */
    public IdentityHeaderFilter() {
        this(IdentityHeaders.GATEWAY_SIGNED, null, null);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
        String userIdHeader = request.getHeader(IdentityHeaders.USER_ID);
        if (StringUtils.hasText(userIdHeader)) {
            // SF-5：身份头来源校验 —— 缺失或不匹配可信来源标记直接拒绝，绝不建立身份
            if (!isFromTrustedSource(request)) {
                log.warn("[ypbin-starter] 身份头 {} 的来源标记缺失或不匹配，拒绝建立身份（直连伪造或配置不一致）",
                    LogSanitizer.sanitize(request.getRequestURI()));
                writeRejected(response);
                return;
            }
            Long userId = parseLongHeader(userIdHeader, IdentityHeaders.USER_ID);
            if (userId == null) {
                // userId 是身份锚点：解析失败视同无有效身份头，不构建登录用户，放行请求
                log.debug("[ypbin-starter] 身份头 {} 非合法 Long（{}），本次请求按无身份放行",
                    IdentityHeaders.USER_ID, LogSanitizer.sanitize(userIdHeader));
            } else {
                LoginUser loginUser = new LoginUser();
                loginUser.setId(userId);
                String username = request.getHeader(IdentityHeaders.USER_NAME);
                if (StringUtils.hasText(username)) {
                    loginUser.setUsername(username);
                }
                Long tenantId = parseLongHeader(request.getHeader(IdentityHeaders.TENANT_ID),
                    IdentityHeaders.TENANT_ID);
                if (tenantId != null) {
                    loginUser.setTenantId(tenantId);
                }
                Long deptId = parseLongHeader(request.getHeader(IdentityHeaders.DEPT_ID),
                    IdentityHeaders.DEPT_ID);
                if (deptId != null) {
                    loginUser.setDeptId(deptId);
                }
                String roles = request.getHeader(IdentityHeaders.ROLES);
                if (StringUtils.hasText(roles)) {
                    Set<String> roleSet = Arrays.stream(roles.split(","))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toSet());
                    loginUser.setRoles(roleSet);
                }
                IdentityContext.setLoginUser(loginUser);
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            IdentityContext.clear();
        }
    }

    /**
     * 判定当前请求的身份头来源是否可信。
     *
     * <p>要求来源标记头存在且值（去除首尾空白后）与期望值完全一致；期望值未配置时
     * （{@code null}）恒为不可信（fail-closed）。</p>
     *
     * @param request 当前请求
     * @return 来源可信返回 {@code true}
     */
    private boolean isFromTrustedSource(HttpServletRequest request) {
        if (!StringUtils.hasText(trustedSourceToken) || !StringUtils.hasText(trustedSourceHeader)) {
            // 无期望值/无头名：无法校验来源，按不可信处理（fail-closed）
            return false;
        }
        String actual = request.getHeader(trustedSourceHeader);
        return StringUtils.hasText(actual) && trustedSourceToken.equals(actual.trim());
    }

    /**
     * 写拒绝响应（遵循全局约定：HTTP 200 + 业务码，R 结构）。
     */
    private void writeRejected(HttpServletResponse response) throws IOException {
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
            log.warn("[ypbin-starter] 身份拒绝响应序列化失败，回退固定报文", e);
            response.getWriter().write(REJECTED_FALLBACK_JSON);
        }
    }

    /**
     * 解析 Long 型身份头：缺失/空白返回 {@code null}；非数字记 debug 并返回 {@code null}（不抛异常中断请求）。
     */
    @Nullable
    private static Long parseLongHeader(String headerValue, String headerName) {
        if (!StringUtils.hasText(headerValue)) {
            return null;
        }
        try {
            return Long.valueOf(headerValue.trim());
        } catch (NumberFormatException e) {
            log.debug("[ypbin-starter] 身份头 {} 非合法 Long（{}），忽略该字段",
                LogSanitizer.sanitize(headerName), LogSanitizer.sanitize(headerValue));
            return null;
        }
    }
}

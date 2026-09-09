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
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 内部身份头过滤器（微服务下游专用）。
 *
 * <p>网关校验 token 后签发 {@code X-User-Id/X-User-Name/X-Tenant-Id/X-Dept-Id/X-Roles}
 * 可信身份头。各业务服务装配本过滤器，从这些头构建 {@link LoginUser}
 * 写入 {@link IdentityContext}，供业务代码无感知读取当前用户——服务自身不再校验 token。</p>
 *
 * <p><strong>默认关闭</strong>：本过滤器仅在 {@code ypbin.security.identity.enabled=true} 时由
 * {@link IdentityAutoConfiguration} 装配。仅当服务位于可信网关之后、且网关负责清洗外部
 * {@code X-User-Id/X-Roles} 等头并签发内部身份头时才应显式开启；若服务可被外部直接访问，
 * 严禁开启（否则外部请求可伪造身份头冒充已认证用户）。</p>
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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
        String userIdHeader = request.getHeader(IdentityHeaders.USER_ID);
        if (StringUtils.hasText(userIdHeader)) {
            Long userId = parseLongHeader(userIdHeader, IdentityHeaders.USER_ID);
            if (userId == null) {
                // userId 是身份锚点：解析失败视同无有效身份头，不构建登录用户，放行请求
                log.debug("[ypbin-starter] 身份头 {} 非合法 Long（{}），本次请求按无身份放行",
                    IdentityHeaders.USER_ID, userIdHeader);
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
     * 解析 Long 型身份头：缺失/空白返回 {@code null}；非数字记 debug 并返回 {@code null}（不抛异常中断请求）。
     */
    private static Long parseLongHeader(String headerValue, String headerName) {
        if (!StringUtils.hasText(headerValue)) {
            return null;
        }
        try {
            return Long.valueOf(headerValue.trim());
        } catch (NumberFormatException e) {
            log.debug("[ypbin-starter] 身份头 {} 非合法 Long（{}），忽略该字段", headerName, headerValue);
            return null;
        }
    }
}

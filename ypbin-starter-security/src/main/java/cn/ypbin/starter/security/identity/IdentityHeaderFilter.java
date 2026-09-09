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
 * @author wenbin
 * @since 2026-09-01
 */
public class IdentityHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
        String userId = request.getHeader(IdentityHeaders.USER_ID);
        if (StringUtils.hasText(userId)) {
            LoginUser loginUser = new LoginUser();
            loginUser.setId(Long.valueOf(userId));
            String username = request.getHeader(IdentityHeaders.USER_NAME);
            if (StringUtils.hasText(username)) {
                loginUser.setUsername(username);
            }
            String tenantId = request.getHeader(IdentityHeaders.TENANT_ID);
            if (StringUtils.hasText(tenantId)) {
                loginUser.setTenantId(Long.valueOf(tenantId));
            }
            String deptId = request.getHeader(IdentityHeaders.DEPT_ID);
            if (StringUtils.hasText(deptId)) {
                loginUser.setDeptId(Long.valueOf(deptId));
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
        try {
            filterChain.doFilter(request, response);
        } finally {
            IdentityContext.clear();
        }
    }
}

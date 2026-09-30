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

import cn.ypbin.starter.security.satoken.StpPermissionAdapter;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 开放 API「虚拟主体 → scopes」的**通用工具**（纯函数，零 IO；可在业务服务与单测中直接使用）。
 *
 * <p><b>背景</b>：开放 API 用 API Key 鉴权时，网关为该请求注入一个**虚拟主体**（保留用户 ID 段），
 * 并把该 Key 的 scopes 放进既有 {@code X-Roles} 头（已被 {@link IdentityHeaderFilter} 解析进
 * {@link cn.ypbin.starter.security.core.LoginUser#getRoles()}）。下游服务的权限解析据此把
 * scopes 当作权限码。本工具提供这条链路上**每个服务都需要的通用判定**：</p>
 * <ul>
 *   <li>{@link #isVirtualPrincipal(Long)} —— 保留虚拟 ID 段判定；</li>
 *   <li>{@link #scopesToPermissionCodes(Collection)} —— scopes → 权限码（trim/去空/去重/剥离 pattern）；</li>
 *   <li>{@link #strippedScopes(Collection)} —— 报告被剥离的项（供日志，避免静默）。</li>
 * </ul>
 *
 * <p><b>🔴 为什么必须剥离一切含 {@code *} 的 scope（本类存在的核心安全理由）</b>：
 * Sa-Token 把账号的权限码当 <b>pattern</b> 走 {@code SaFoxUtil.vagueMatch} 做模糊匹配
 * （见 {@code StpPermissionAdapter} 类注释）⇒ 形如 {@code iot:*}、{@code iot:device:*}、
 * {@code *:*} 的 <b>段内星号同样能命中</b>真实权限码（如 {@code iot:*} 可命中
 * {@code iot:debug:send} 这类高危命令下发）。只剔除 {@code *} 与 {@code *:*:*} 两种精确形态
 * 会漏掉这些 pattern，导致「只该看数据的 Key 却拿到未授予的能力」。
 * 因此在底层统一剥离一切含 {@code *} 的 scope，业务方在之上再看自己的白名单。</p>
 *
 * <p><b>虚拟 ID 段为什么取负数高位</b>：真实用户 ID 是正数雪花 ID ⇒ 负数高位段与真实用户空间
 * <b>永不重叠</b>；且避开 Sa-Token 哨兵值 {@code -3/-4/-5}（本类默认上界 {@code -1_000_000_000}
 * 使所有小负数仍按「非虚拟」处理，保持既有语义）。</p>
 *
 * @author wenbin
 * @since 2026-09-30
 */
public final class VirtualPrincipalScopes {

    private static final Logger log = LoggerFactory.getLogger(VirtualPrincipalScopes.class);

    /**
     * 默认虚拟用户 ID 上界：{@code userId <= 本值} 即视为虚拟主体。
     *
     * <p>真实用户是正数雪花 ID ⇒ 负数高位段永不重叠；避开 Sa-Token 哨兵值 {@code -3/-4/-5}。</p>
     */
    public static final long DEFAULT_VIRTUAL_USER_ID_MAX = -1_000_000_000L;

    /** Sa-Token 官方「全权限」通配符（引用 {@link StpPermissionAdapter#ANY}，避免复制值漂移）。 */
    public static final String ANY_WILDCARD = StpPermissionAdapter.ANY;

    /** 平台超管约定码（引用 {@link StpPermissionAdapter#SUPER_ADMIN}）。 */
    public static final String SUPER_ADMIN = StpPermissionAdapter.SUPER_ADMIN;

    private VirtualPrincipalScopes() {
    }

    /**
     * 判定是否为开放 API 的虚拟主体。
     *
     * @param userId 解析出的用户 ID（可为 {@code null}）
     * @return 虚拟主体返回 {@code true}
     */
    public static boolean isVirtualPrincipal(Long userId) {
        return userId != null && userId <= DEFAULT_VIRTUAL_USER_ID_MAX;
    }

    /**
     * scope 是否含通配符/pattern 形态（含 {@code *} 即算）。
     *
     * @param scope 待判 scope
     * @return 含 {@code *} 返回 {@code true}
     */
    public static boolean containsWildcard(String scope) {
        return scope != null && scope.indexOf('*') >= 0;
    }

    /**
     * 把 scopes 转成权限码（trim、去空、去重、**剥离一切含 {@code *} 的 pattern**）。
     *
     * @param scopes 原始 scopes（可空）
     * @return 权限码列表（**绝不返回 {@code null}**）
     */
    public static List<String> scopesToPermissionCodes(Collection<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String scope : scopes) {
            if (scope == null) {
                continue;
            }
            String token = scope.trim();
            if (token.isEmpty() || containsWildcard(token)) {
                continue;
            }
            out.add(token);
        }
        return List.copyOf(out);
    }

    /**
     * 挑出**会被剥离**的 scopes（含 {@code *} 的 pattern；空白项不算剥离——那是"没填"）。
     *
     * @param scopes 原始 scopes
     * @return 被剥离项（保持首次出现顺序）
     */
    public static List<String> strippedScopes(Collection<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String scope : scopes) {
            if (scope == null) {
                continue;
            }
            String token = scope.trim();
            if (!token.isEmpty() && containsWildcard(token)) {
                out.add(token);
            }
        }
        return List.copyOf(out);
    }

    /**
     * 便捷：从权限码列表判断是否有任一码是通配符（供业务侧 fail-closed 前做日志归因）。
     *
     * @param permissionCodes 权限码
     * @return 含通配符返回 {@code true}
     */
    public static boolean containsAnyWildcard(Collection<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return false;
        }
        for (String code : permissionCodes) {
            if (containsWildcard(code)) {
                return true;
            }
        }
        return false;
    }
}

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
package cn.ypbin.starter.security.satoken;

import cn.dev33.satoken.stp.StpInterface;
import cn.ypbin.starter.security.core.PermissionProvider;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sa-Token 权限接口适配器。
 *
 * <p>将框架的 {@link PermissionProvider} 扩展点桥接到 Sa-Token 的 {@link StpInterface}，
 * 使业务方无需直接依赖 Sa-Token API 即可提供权限与角色数据。</p>
 *
 * <p><strong>平台超管约定 ≠ Sa-Token 通配符</strong>：本仓与下游宿主用 {@link #SUPER_ADMIN}
 * （{@code *:*:*}）表示平台超管，而 Sa-Token 的「全权限」通配符是 {@link #ANY}（单个 {@code *}）。
 * {@code *:*:*} 在 Sa-Token 内部只作为普通权限码参与模糊匹配（账号权限码当 pattern 走
 * {@code SaFoxUtil.vagueMatch}），实测只能命中<strong>含两个及以上冒号</strong>的权限码：
 * {@code system:user:add}（2 个冒号）、{@code a:b:c:d}（3 个）能过，而 {@code user:add}（1 个）、
 * {@code single}（0 个）不能——于是超管反而被挡在短权限码之外。本适配器在返回前为含 {@code *:*:*} 的集合
 * 补上 {@code *}，保留超管语义；原始权限码原样保留，不做删除或改写。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class StpPermissionAdapter implements StpInterface {

    private static final Logger log = LoggerFactory.getLogger(StpPermissionAdapter.class);

    /** 平台超管权限码（本仓与下游宿主约定，<strong>不是</strong> Sa-Token 官方语义）。 */
    public static final String SUPER_ADMIN = "*:*:*";

    /** Sa-Token 官方「全权限」通配符：命中任意权限码（见 {@code SaStrategy#hasElement} 的模糊匹配）。 */
    public static final String ANY = "*";

    private final PermissionProvider permissionProvider;

    public StpPermissionAdapter(PermissionProvider permissionProvider) {
        this.permissionProvider = permissionProvider;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return withSuperAdminWildcard(permissionProvider.getPermissions(loginId, loginType), "权限");
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return withSuperAdminWildcard(permissionProvider.getRoles(loginId, loginType), "角色");
    }

    /**
     * 把宿主的平台超管约定翻译成 Sa-Token 能识别的通配符。
     *
     * @param codes 宿主返回的权限码/角色码集合，可为 {@code null}
     * @param kind  集合语义（仅用于告警文案）
     * @return 非 {@code null} 的集合；含 {@link #SUPER_ADMIN} 且尚无 {@link #ANY} 时追加 {@link #ANY}
     */
    private static List<String> withSuperAdminWildcard(List<String> codes, String kind) {
        if (codes == null) {
            // fail-closed：宿主违约返回 null 时不授予任何权限，但绝不静默——否则「超管突然没权限」无从排查
            log.warn("[ypbin-starter] PermissionProvider 返回 null {}码集合，已按空集合处理（不授予任何{}）",
                kind, kind);
            return List.of();
        }
        if (codes.isEmpty() || !codes.contains(SUPER_ADMIN) || codes.contains(ANY)) {
            return codes;
        }
        List<String> normalized = new ArrayList<>(codes.size() + 1);
        normalized.addAll(codes);
        normalized.add(ANY);
        return normalized;
    }
}

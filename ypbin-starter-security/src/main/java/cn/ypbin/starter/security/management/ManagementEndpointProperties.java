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

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理端点权限守卫配置项（{@code ypbin.security.management.*}）。
 *
 * <p><strong>解决的问题（反馈 UP-5）</strong>：{@code /actuator/**} 在默认权限模型里是"无主面"——
 * 网关只校验"已登录"、下游还把 actuator 排除在 Sa-Token 拦截之外，任何已登录账号都能读平台级指标。
 * 本守卫在 servlet 层对管理端点做<b>权限码</b>收口（fail-closed）。</p>
 *
 * <p><strong>默认关闭</strong>：{@code guard-enabled} 默认为 {@code false}，宿主显式开启。
 * 开启后按 fail-closed 语义工作：{@code required-permission} 未配置 → 非公开端点一律拒绝；
 * 当前用户未登录 → 拒绝；权限不足 → 拒绝。公开端点（{@link #publicPaths}，默认
 * {@code health}/{@code info}）始终放行，不影响可用性探针。</p>
 *
 * <p>权限判定复用安全模块的 {@code PermissionProvider} 扩展点（宿主已有的权限码体系），
 * 平台超管约定（{@code *:*:*}）自动放行。</p>
 *
 * @author wenbin
 * @since 2026-09-28
 */
@ConfigurationProperties(prefix = ManagementEndpointProperties.PREFIX)
public class ManagementEndpointProperties {

    public static final String PREFIX = "ypbin.security.management";

    /** 是否启用管理端点权限守卫，默认关闭（显式开启，避免破坏未配置宿主） */
    private boolean guardEnabled = false;

    /**
     * 管理端点基路径，需与 {@code management.endpoints.web.base-path} 一致（Spring Boot 默认
     * {@code /actuator}）。守卫只对该路径下的端点做权限校验。
     */
    private String basePath = "/actuator";

    /**
     * 放行的公开端点（相对 {@link #basePath} 的路径，支持 Ant 风格），默认放行
     * {@code health}/{@code info}——这两个端点是有意公开的（可用性探针），不得误伤。
     */
    private List<String> publicPaths = new ArrayList<>(List.of("/health", "/info"));

    /**
     * 访问其余管理端点所需的权限码（宿主权限体系中的码，经 {@code PermissionProvider} 校验）。
     *
     * <p><strong>fail-closed</strong>：未配置时非公开管理端点一律拒绝（无法判定权限即不放行）。</p>
     */
    private String requiredPermission = "";

    public boolean isGuardEnabled() {
        return guardEnabled;
    }

    public void setGuardEnabled(boolean guardEnabled) {
        this.guardEnabled = guardEnabled;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public String getRequiredPermission() {
        return requiredPermission;
    }

    public void setRequiredPermission(String requiredPermission) {
        this.requiredPermission = requiredPermission;
    }
}

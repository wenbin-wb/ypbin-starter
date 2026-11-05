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

import cn.ypbin.starter.security.core.PermissionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import tools.jackson.databind.ObjectMapper;

/**
 * 管理端点权限守卫自动配置。
 *
 * <p>宿主显式开启 {@code ypbin.security.management.guard-enabled=true} 时装配
 * {@link ManagementEndpointGuard}：对 {@code management.endpoints.web.base-path}（默认
 * {@code /actuator}）下的端点做权限码收口（fail-closed），权限数据复用
 * {@link PermissionProvider} 扩展点（由 security 模块默认装配空实现、宿主提供真实实现后接管）。
 * 与 {@code PlatformAccessAutoConfiguration} 相同，为显式开启（opt-in）、不破坏未配置宿主。</p>
 *
 * @author wenbin
 * @since 2026-09-28
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = ManagementEndpointProperties.PREFIX, name = "guard-enabled",
    havingValue = "true", matchIfMissing = false)
// Servlet 专属（OncePerRequestFilter）：WebFlux 应用（网关）不装配
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(ManagementEndpointProperties.class)
public class ManagementEndpointGuardAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ManagementEndpointGuardAutoConfiguration.class);

    /**
     * 装配管理端点权限守卫，顺序置于身份头过滤器（{@link Ordered#HIGHEST_PRECEDENCE}）之后，
     * 确保能读到当前用户身份（微服务模式经身份头、单体模式经 Sa-Token 会话）。
     *
     * <p><strong>为什么两个依赖都是弱依赖</strong>：{@code PermissionProvider} 只由
     * {@code SecurityAutoConfiguration} 提供，而它被 {@code ypbin.security.enabled=true} 守门——宿主
     * 「关掉安全模块但打开管理端点收口」时（两个公开开关的合法组合）把它写成必需入参，会让应用启动直接
     * 抛 {@code UnsatisfiedDependencyException}。故：</p>
     * <ul>
     *   <li>{@code PermissionProvider} 缺席时退化为「空权限数据源」= 一律拒绝（<b>fail-closed</b>），
     *       并 WARN 一次提示该组合下管理端点将不可访问；</li>
     *   <li>{@code ObjectMapper} 本就是可选的（{@link ManagementEndpointGuard} 内部有固定报文兜底）。</li>
     * </ul>
     *
     * @param properties         守卫配置
     * @param permissionProviders 权限数据源（security 模块默认提供空实现，宿主实现后自动接管）
     * @param objectMappers      拒绝响应序列化器（可缺席）
     * @return 过滤器注册
     */
    @Bean
    public FilterRegistrationBean<ManagementEndpointGuard> managementEndpointGuardRegistration(
            ManagementEndpointProperties properties, ObjectProvider<PermissionProvider> permissionProviders,
            ObjectProvider<ObjectMapper> objectMappers) {
        PermissionProvider permissionProvider = permissionProviders.getIfAvailable();
        if (permissionProvider == null) {
            log.warn("[ypbin-starter] 已开启 ypbin.security.management.guard-enabled=true 但容器中没有 "
                + "PermissionProvider（通常因为 ypbin.security.enabled=false）；管理端点守卫将以空权限数据源"
                + "运行，即一律拒绝访问（fail-closed）。如需放行请提供 PermissionProvider 实现");
            permissionProvider = new PermissionProvider() {
            };
        }
        FilterRegistrationBean<ManagementEndpointGuard> registration = new FilterRegistrationBean<>(
            new ManagementEndpointGuard(properties, permissionProvider, objectMappers.getIfAvailable()));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}

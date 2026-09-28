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

    /**
     * 装配管理端点权限守卫，顺序置于身份头过滤器（{@link Ordered#HIGHEST_PRECEDENCE}）之后，
     * 确保能读到当前用户身份（微服务模式经身份头、单体模式经 Sa-Token 会话）。
     *
     * @param properties         守卫配置
     * @param permissionProvider 权限数据源（security 模块默认提供空实现，宿主实现后自动接管）
     * @param objectMapper       拒绝响应序列化器
     * @return 过滤器注册
     */
    @Bean
    public FilterRegistrationBean<ManagementEndpointGuard> managementEndpointGuardRegistration(
            ManagementEndpointProperties properties, PermissionProvider permissionProvider,
            ObjectMapper objectMapper) {
        FilterRegistrationBean<ManagementEndpointGuard> registration = new FilterRegistrationBean<>(
            new ManagementEndpointGuard(properties, permissionProvider, objectMapper));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}

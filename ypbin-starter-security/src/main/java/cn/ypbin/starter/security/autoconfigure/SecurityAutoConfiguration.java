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
package cn.ypbin.starter.security.autoconfigure;

import cn.dev33.satoken.stp.StpInterface;
import cn.ypbin.starter.security.core.PermissionProvider;
import cn.ypbin.starter.security.satoken.StpPermissionAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 安全模块自动配置。
 *
 * <p>装配权限数据源默认实现与 Sa-Token 适配器。当业务方未提供
 * {@link PermissionProvider} 时使用返回空权限的默认实现，保证零配置可启动；
 * 提供实现后即接管注解鉴权的数据来源。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnClass(StpInterface.class)
@ConditionalOnProperty(prefix = "ypbin.security", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityAutoConfiguration.class);

    /**
     * 默认权限数据源：返回空权限/角色。业务方提供自定义实现即可覆盖。
     */
    @Bean
    @ConditionalOnMissingBean
    public PermissionProvider permissionProvider() {
        log.warn("[ypbin-starter] 使用默认空权限数据源，注解鉴权将始终无权限。请提供 PermissionProvider 实现。");
        return new PermissionProvider() {
        };
    }

    /**
     * Sa-Token 权限接口适配器。
     */
    @Bean
    @ConditionalOnMissingBean
    public StpInterface stpInterface(PermissionProvider permissionProvider) {
        return new StpPermissionAdapter(permissionProvider);
    }
}

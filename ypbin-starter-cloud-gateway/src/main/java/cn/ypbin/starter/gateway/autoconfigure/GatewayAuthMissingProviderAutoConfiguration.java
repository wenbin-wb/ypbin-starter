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
package cn.ypbin.starter.gateway.autoconfigure;

import cn.ypbin.starter.gateway.auth.GatewayAuthProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.gateway.filter.GlobalFilter;

/**
 * 网关统一认证「已开启但无认证提供方」的启动期告警。
 *
 * <p>{@code GatewayAutoConfiguration#gatewayAuthGlobalFilter} 同时带 {@code @ConditionalOnBean
 * (GatewayAuthProvider.class)}：宿主设置 {@code ypbin.gateway.auth.enabled=true} 却没提供
 * {@link GatewayAuthProvider} 时，鉴权过滤器不会被注册——网关会「看起来启用了统一认证」而实际不做鉴权，
 * 且启动期无任何提示（安全相关）。本配置负责把这种「能力未生效」显式说出来（禁静默不生效）。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
@AutoConfiguration
@ConditionalOnClass(GlobalFilter.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(prefix = "ypbin.gateway", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "ypbin.gateway.auth", name = "enabled", havingValue = "true")
public class GatewayAuthMissingProviderAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GatewayAuthMissingProviderAutoConfiguration.class);

    GatewayAuthMissingProviderAutoConfiguration(ObjectProvider<GatewayAuthProvider> authProvider) {
        if (authProvider.getIfAvailable() == null) {
            log.warn("[ypbin-starter] 已开启 ypbin.gateway.auth.enabled=true，但容器中没有 GatewayAuthProvider，"
                + "网关鉴权过滤器不会注册（请求将不经统一认证直接转发）；请提供 GatewayAuthProvider Bean。");
        }
    }
}

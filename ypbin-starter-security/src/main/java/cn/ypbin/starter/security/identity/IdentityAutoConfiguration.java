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

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * 微服务下游身份头自动配置。
 *
 * <p>为业务服务装配 {@link IdentityHeaderFilter}：解析网关签发的内部身份头构建
 * {@link IdentityContext}。各微服务只需引入 starter-security 且开启
 * {@code ypbin.security.identity.enabled} 即自动生效。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ypbin.security.identity", name = "enabled", havingValue = "true",
    matchIfMissing = true)
public class IdentityAutoConfiguration {

    @Bean
    public FilterRegistrationBean<IdentityHeaderFilter> identityHeaderFilterRegistration() {
        FilterRegistrationBean<IdentityHeaderFilter> registration =
            new FilterRegistrationBean<>(new IdentityHeaderFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}

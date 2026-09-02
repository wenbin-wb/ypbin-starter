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
package cn.ypbin.starter.security.platform;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * 平台访问控制自动配置。
 *
 * <p>默认放行的 {@link PlatformUserChecker}（业务方实现后自动覆盖），并装配
 * {@link PlatformAccessAspect} 切面。可通过 {@code ypbin.security.platform.enabled}
 * 关闭（默认开启）。</p>
 *
 * @author wenbin
 * @since 2026-09-01
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ypbin.security.platform", name = "enabled", havingValue = "true",
    matchIfMissing = true)
// Servlet 专属（平台访问切面拦截 Controller）：WebFlux 应用（网关）不装配
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PlatformAccessAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PlatformUserChecker platformUserChecker() {
        return new PlatformUserChecker() {
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public PlatformAccessAspect platformAccessAspect(PlatformUserChecker checker) {
        return new PlatformAccessAspect(checker);
    }
}

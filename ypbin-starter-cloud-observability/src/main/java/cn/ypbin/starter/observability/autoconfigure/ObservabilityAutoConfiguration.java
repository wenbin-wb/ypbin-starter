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
package cn.ypbin.starter.observability.autoconfigure;

import cn.ypbin.starter.observability.web.RequestIdMdcFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * 可观测性自动配置。
 *
 * <p>仅在 Servlet Web 应用且 {@code ypbin.observability.enabled=true}（默认）时生效，注册请求 ID
 * MDC 关联过滤器，使日志携带 {@code X-Request-Id}。若引入 Micrometer Tracing 桥接与 exporter，
 * 该请求 ID 可与 traceId 一并出现在日志中，实现日志与链路的关联。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
@AutoConfiguration
@ConditionalOnClass(HttpServletRequest.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "ypbin.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ObservabilityProperties.class)
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FilterRegistrationBean<RequestIdMdcFilter> requestIdMdcFilterRegistration(ObservabilityProperties properties) {
        FilterRegistrationBean<RequestIdMdcFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestIdMdcFilter(properties.getRequestIdHeader(), properties.getMdcKey()));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("ypbinRequestIdMdcFilter");
        return registration;
    }
}

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
package cn.ypbin.starter.log.autoconfigure;

import cn.ypbin.starter.log.interceptor.AccessLogInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全量访问日志自动配置。
 *
 * <p>仅在 Servlet Web 环境、引入 spring-webmvc 且 {@code ypbin.log.access.enabled=true} 时生效。
 * 注册 {@link AccessLogInterceptor} 记录全量请求流水，与基于 {@code @Log} 注解的操作日志互补。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnClass(WebMvcConfigurer.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "ypbin.log.access", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AccessLogProperties.class)
public class AccessLogAutoConfiguration {

    @Bean
    public WebMvcConfigurer accessLogWebMvcConfigurer(AccessLogProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new AccessLogInterceptor())
                    .addPathPatterns(properties.getPathPatterns())
                    .excludePathPatterns(properties.getExcludePathPatterns());
            }
        };
    }
}

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
package cn.ypbin.starter.web.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * API 版本管理自动配置（Spring Framework 7 原生版本化 API 支持）。
 *
 * <p>启用后即可在 Controller 上用 {@code @GetMapping(value = "/user", version = "1.0")} 声明版本，
 * 同一路径的不同版本由框架按版本条件路由，无需自建 URL 前缀或网关路由策略。版本解析方式由
 * {@code ypbin.web.api-version.resolver} 决定（请求头 / 查询参数 / 路径段）。</p>
 *
 * <p>仅在 Servlet Web 应用且 {@code ypbin.web.api-version.enabled=true}（默认关闭）时生效，
 * 避免改变既有路由匹配行为。</p>
 *
 * @author wenbin
 * @since 2026-09-13
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({WebMvcConfigurer.class, ApiVersionConfigurer.class})
@ConditionalOnProperty(prefix = ApiVersionProperties.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ApiVersionProperties.class)
public class ApiVersionAutoConfiguration {

    /**
     * 注册 API 版本解析策略：显式声明解析入口、缺省版本与支持版本清单。
     *
     * @param properties 版本管理配置
     * @return WebMvc 版本化配置
     */
    @Bean
    public WebMvcConfigurer ypbinApiVersionWebMvcConfigurer(ApiVersionProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void configureApiVersioning(ApiVersionConfigurer configurer) {
                switch (properties.getResolver()) {
                    case QUERY_PARAM -> configurer.useQueryParam(properties.getQueryParam());
                    case PATH_SEGMENT -> configurer.usePathSegment(properties.getPathSegmentIndex());
                    case HEADER -> configurer.useRequestHeader(properties.getHeaderName());
                }
                configurer.setVersionRequired(properties.isVersionRequired());
                if (properties.getDefaultVersion() != null && !properties.getDefaultVersion().isBlank()) {
                    configurer.setDefaultVersion(properties.getDefaultVersion());
                }
                if (properties.getSupportedVersions() != null
                        && !properties.getSupportedVersions().isEmpty()) {
                    configurer.addSupportedVersions(
                        properties.getSupportedVersions().toArray(String[]::new));
                } else {
                    configurer.detectSupportedVersions(true);
                }
            }
        };
    }
}

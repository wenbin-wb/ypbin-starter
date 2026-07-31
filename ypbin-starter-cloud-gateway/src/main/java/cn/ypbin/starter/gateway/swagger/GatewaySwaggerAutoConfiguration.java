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
package cn.ypbin.starter.gateway.swagger;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.properties.AbstractSwaggerUiConfigProperties.SwaggerUrl;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;

/**
 * Gateway Swagger 文档聚合自动配置。
 *
 * <p>仅在引入 SpringDoc WebFlux UI 且开启聚合后生效，从 Gateway 路由表读取下游服务名，
 * 自动填充 {@code springdoc.swagger-ui.urls}，使前端可通过网关 Swagger UI 下拉框切换查看所有微服务 API。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
@AutoConfiguration
@ConditionalOnClass(SwaggerUiConfigProperties.class)
@ConditionalOnProperty(prefix = "ypbin.gateway.swagger", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(GatewaySwaggerAggregationProperties.class)
public class GatewaySwaggerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GatewaySwaggerAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(name = "ypbinGatewaySwaggerUrlsInitializer")
    public Object ypbinGatewaySwaggerUrlsInitializer(
        RouteDefinitionLocator routeDefinitionLocator,
        SwaggerUiConfigProperties swaggerUiConfigProperties,
        GatewaySwaggerAggregationProperties aggregationProperties) {

        Set<SwaggerUrl> existingUrls = new HashSet<>();
        if (swaggerUiConfigProperties.getUrls() != null) {
            existingUrls.addAll(swaggerUiConfigProperties.getUrls());
        }

        Flux.fromIterable(routeDefinitionLocator.getRouteDefinitions().collectList().block())
            .filter(definition -> aggregationProperties.getExcludedRoutePrefixes()
                .stream().noneMatch(prefix -> definition.getId().startsWith(prefix)))
            .mapNotNull(GatewaySwaggerAutoConfiguration::extractServiceName)
            .distinct()
            .doOnNext(serviceName -> {
                String url = "/" + serviceName + aggregationProperties.getApiDocsPath();
                String groupName = aggregationProperties.getGroupName();
                if (!"default".equals(groupName)) {
                    url = url + "/" + groupName;
                }
                SwaggerUrl swaggerUrl = new SwaggerUrl(serviceName, url, serviceName);
                existingUrls.add(swaggerUrl);
                log.debug("[ypbin-starter] Swagger aggregated: {} -> {}", serviceName, url);
            })
            .blockLast();
        swaggerUiConfigProperties.setUrls(existingUrls);
        return new Object();
    }

    private static String extractServiceName(RouteDefinition definition) {
        URI uri = definition.getUri();
        if (uri == null || !"lb".equals(uri.getScheme())) {
            return null;
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return null;
        }
        return host;
    }
}

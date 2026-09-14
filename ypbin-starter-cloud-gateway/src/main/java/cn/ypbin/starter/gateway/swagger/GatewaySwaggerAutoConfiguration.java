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
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
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

    /**
     * 路由表解析阻塞上限：路由定位器（如 Nacos 动态路由）未就绪时不得无限阻塞启动，
     * 超时后日志告警并以已有路由继续（聚合信息可在路由就绪后通过刷新补齐）。
     */
    private static final Duration ROUTE_RESOLVE_TIMEOUT = Duration.ofSeconds(10);

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

        List<RouteDefinition> definitions = routeDefinitionLocator.getRouteDefinitions()
            .collectList()
            .block(ROUTE_RESOLVE_TIMEOUT);
        if (definitions == null) {
            log.warn("[ypbin-starter] 路由表在 {} 内未就绪，Swagger 聚合跳过本次刷新",
                ROUTE_RESOLVE_TIMEOUT);
            return new Object();
        }

        Flux.fromIterable(definitions)
            .filter(definition -> definition.getId() != null
                && aggregationProperties.getExcludedRoutePrefixes()
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
            .blockLast(ROUTE_RESOLVE_TIMEOUT);
        swaggerUiConfigProperties.setUrls(existingUrls);
        return new Object();
    }

    @Nullable
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

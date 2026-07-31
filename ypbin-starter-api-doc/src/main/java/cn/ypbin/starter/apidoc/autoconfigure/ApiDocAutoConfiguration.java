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
package cn.ypbin.starter.apidoc.autoconfigure;

import cn.ypbin.starter.apidoc.annotation.ApiOrder;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;

/**
 * API 文档自动配置。
 *
 * <p>基于 SpringDoc 构建 {@link OpenAPI}、分组文档、全局安全头与 {@link ApiOrder} 排序。
 * 仅在 {@code ypbin.api-doc.enabled=true} 时生效，业务方可提供同类型 Bean 覆盖。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@ConditionalOnProperty(prefix = "ypbin.api-doc", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ApiDocProperties.class)
public class ApiDocAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ApiDocAutoConfiguration.class);

    private static final String ORDER_EXTENSION = "x-order";

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI ypbinOpenAPI(ApiDocProperties properties) {
        Components components = new Components();
        for (String securityHeader : properties.getSecurityHeaders()) {
            components.addSecuritySchemes(securityHeader, new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(securityHeader));
        }
        Info info = new Info()
            .title(properties.getTitle())
            .description(properties.getDescription())
            .version(properties.getVersion());

        ApiDocProperties.Contact contact = properties.getContact();
        if (StringUtils.hasText(contact.getName()) || StringUtils.hasText(contact.getEmail())) {
            info.contact(new Contact()
                .name(contact.getName())
                .email(contact.getEmail())
                .url(contact.getUrl()));
        }

        ApiDocProperties.License license = properties.getLicense();
        if (StringUtils.hasText(license.getName())) {
            info.license(new License()
                .name(license.getName())
                .url(license.getUrl()));
        }

        log.debug("[ypbin-starter] OpenAPI configured, title={}.", properties.getTitle());
        return new OpenAPI().info(info).components(components);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ypbin.api-doc", name = "default-group-enabled", havingValue = "true", matchIfMissing = true)
    public GroupedOpenApi ypbinDefaultGroupedOpenApi(ApiDocProperties properties) {
        GroupedOpenApi.Builder builder = GroupedOpenApi.builder()
            .group(properties.getGroupName())
            .pathsToMatch(properties.getPathsToMatch().toArray(new String[0]))
            .pathsToExclude(properties.getPathsToExclude().toArray(new String[0]));
        if (!properties.getPackagesToScan().isEmpty()) {
            builder.packagesToScan(properties.getPackagesToScan().toArray(new String[0]));
        }
        if (!properties.getPackagesToExclude().isEmpty()) {
            builder.packagesToExclude(properties.getPackagesToExclude().toArray(new String[0]));
        }
        log.debug("[ypbin-starter] GroupedOpenApi configured, group={}.", properties.getGroupName());
        return builder.build();
    }

    /**
     * 全局安全头声明与排序补丁。
     */
    @Bean
    @ConditionalOnMissingBean(name = "ypbinApiDocOperationCustomizer")
    public GlobalOperationCustomizer ypbinApiDocOperationCustomizer(ApiDocProperties properties) {
        return (operation, handlerMethod) -> {
            for (String securityHeader : properties.getSecurityHeaders()) {
                operation.addSecurityItem(new SecurityRequirement().addList(securityHeader));
            }
            if (properties.isOrderEnabled()) {
                Integer order = resolveOrder(handlerMethod);
                if (order != null) {
                    operation.addExtension(ORDER_EXTENSION, order);
                }
                recordTagOrder(operation, handlerMethod);
            }
            return operation;
        };
    }

    /**
     * 按 x-order 重排 paths、按类级 @ApiOrder 重排 tags。
     */
    @Bean
    @ConditionalOnMissingBean(name = "ypbinApiDocOpenApiCustomizer")
    @ConditionalOnProperty(prefix = "ypbin.api-doc", name = "order-enabled", havingValue = "true", matchIfMissing = true)
    public GlobalOpenApiCustomizer ypbinApiDocOpenApiCustomizer() {
        return openApi -> {
            Paths paths = openApi.getPaths();
            if (paths == null || paths.isEmpty()) {
                return;
            }
            Paths ordered = new Paths();
            paths.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, PathItem>>comparingInt(entry -> resolveMinOrder(entry.getValue()))
                    .thenComparing(Map.Entry::getKey))
                .forEach(entry -> ordered.addPathItem(entry.getKey(), entry.getValue()));
            openApi.setPaths(ordered);
            List<Tag> tags = openApi.getTags();
            if (tags != null && !tags.isEmpty()) {
                tags.sort(Comparator.comparingInt((Tag tag) -> tagOrders.getOrDefault(tag.getName(), Integer.MAX_VALUE))
                    .thenComparing(Tag::getName));
            }
        };
    }

    private static Integer resolveOrder(HandlerMethod handlerMethod) {
        ApiOrder methodOrder = handlerMethod.getMethodAnnotation(ApiOrder.class);
        if (methodOrder != null) {
            return methodOrder.value();
        }
        ApiOrder classOrder = handlerMethod.getBeanType().getAnnotation(ApiOrder.class);
        if (classOrder != null) {
            return classOrder.value();
        }
        return null;
    }

    private static int resolveMinOrder(PathItem pathItem) {
        return pathItem.readOperations().stream()
            .filter(op -> op.getExtensions() != null)
            .map(op -> op.getExtensions().get(ORDER_EXTENSION))
            .filter(Objects::nonNull)
            .mapToInt(value -> ((Number) value).intValue())
            .min()
            .orElse(Integer.MAX_VALUE);
    }

    private final Map<String, Integer> tagOrders = new ConcurrentHashMap<>();

    private void recordTagOrder(Operation operation, HandlerMethod handlerMethod) {
        ApiOrder classOrder = handlerMethod.getBeanType().getAnnotation(ApiOrder.class);
        if (classOrder == null || operation.getTags() == null) {
            return;
        }
        operation.getTags().forEach(tag -> tagOrders.putIfAbsent(tag, classOrder.value()));
    }
}

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
import cn.ypbin.starter.gateway.filter.GatewayAuthGlobalFilter;
import cn.ypbin.starter.gateway.filter.HeaderSanitizeGlobalFilter;
import cn.ypbin.starter.gateway.filter.RequestIdGlobalFilter;
import cn.ypbin.starter.gateway.handler.GatewayExceptionHandler;
import cn.ypbin.starter.gateway.ratelimit.AttributeRateLimitGlobalFilter;
import cn.ypbin.starter.gateway.ratelimit.RateLimitProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

/**
 * 网关自动配置。
 *
 * <p>仅在引入 Spring Cloud Gateway 且 {@code ypbin.gateway.enabled=true}（默认）时生效。
 * 装配请求 ID、身份头清洗、WebFlux CORS 与统一异常处理。路由规则由业务方在
 * {@code spring.cloud.gateway.server.webflux.routes} 配置（Spring Cloud Gateway 4.1+ 新前缀），
 * 本模块只提供通用横切能力，不预设路由。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnClass(GlobalFilter.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(prefix = "ypbin.gateway", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({GatewayProperties.class, RateLimitProperties.class})
public class GatewayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RequestIdGlobalFilter requestIdGlobalFilter(GatewayProperties properties) {
        return new RequestIdGlobalFilter(properties.getRequestIdHeader());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ypbin.gateway.header-sanitize", name = "enabled", havingValue = "true", matchIfMissing = true)
    public HeaderSanitizeGlobalFilter headerSanitizeGlobalFilter(GatewayProperties properties) {
        return new HeaderSanitizeGlobalFilter(properties.getHeaderSanitize().getHeaders());
    }

    @Bean
    @ConditionalOnBean(GatewayAuthProvider.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ypbin.gateway.auth", name = "enabled", havingValue = "true")
    public GatewayAuthGlobalFilter gatewayAuthGlobalFilter(
        GatewayAuthProvider authProvider,
        ObjectProvider<ObjectMapper> objectMapperProvider,
        GatewayProperties properties) {
        ObjectMapper mapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new GatewayAuthGlobalFilter(authProvider, mapper, properties.getAuth().getExcludePaths(),
            properties.getAuth().getTrustedSourceHeader(), properties.getAuth().getTrustedSourceToken());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ypbin.gateway.cors", name = "enabled", havingValue = "true")
    public CorsWebFilter corsWebFilter(GatewayProperties properties) {
        GatewayProperties.Cors cors = properties.getCors();
        CorsConfiguration config = new CorsConfiguration();
        cors.getAllowedOriginPatterns().forEach(config::addAllowedOriginPattern);
        cors.getAllowedMethods().forEach(config::addAllowedMethod);
        cors.getAllowedHeaders().forEach(config::addAllowedHeader);
        cors.getExposedHeaders().forEach(config::addExposedHeader);
        config.setAllowCredentials(cors.isAllowCredentials());
        config.setMaxAge(cors.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }

    @Bean
    @ConditionalOnMissingBean
    public GatewayExceptionHandler gatewayExceptionHandler(ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new GatewayExceptionHandler(objectMapperProvider.getIfAvailable(ObjectMapper::new));
    }

    /**
     * 属性驱动限流过滤器（默认关闭，须显式启用；无 Redis 时整个嵌套类被跳过）。
     *
     * <p>条件三重门：① 类路径有 reactive Redis（可选依赖，类级条件——方法级
     * {@code @ConditionalOnClass} 阻止不了 Spring 内省 Bean 方法参数类型）；② 容器里有
     * {@code ReactiveStringRedisTemplate} Bean（业务方提供连接）；③ 显式
     * {@code ypbin.gateway.rate-limit.enabled=true}。缺一即不装配，绝不静默限流。</p>
     */
    @Configuration
    @ConditionalOnClass(ReactiveStringRedisTemplate.class)
    static class RateLimitFilterConfiguration {

        @Bean
        @ConditionalOnBean(ReactiveStringRedisTemplate.class)
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "ypbin.gateway.rate-limit", name = "enabled", havingValue = "true")
        public AttributeRateLimitGlobalFilter attributeRateLimitGlobalFilter(
            ReactiveStringRedisTemplate redis,
            RateLimitProperties properties,
            ObjectProvider<ObjectMapper> objectMapperProvider) {
            return new AttributeRateLimitGlobalFilter(redis, properties,
                objectMapperProvider.getIfAvailable(ObjectMapper::new));
        }
    }
}

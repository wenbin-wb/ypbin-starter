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
package cn.ypbin.starter.gateway.route;

import com.alibaba.nacos.api.config.ConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.annotation.Bean;

/**
 * Nacos 动态路由自动配置。
 *
 * @author wenbin
 * @since 2026-07-31
 */
@AutoConfiguration
@ConditionalOnClass(ConfigService.class)
@ConditionalOnBean({RouteDefinitionLocator.class, RouteDefinitionWriter.class})
@ConditionalOnProperty(prefix = "ypbin.gateway.route.nacos", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(NacosRouteProperties.class)
public class NacosRouteAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public NacosRouteInitializer nacosRouteInitializer(
        ConfigService configService,
        NacosRouteProperties properties,
        ObjectMapper objectMapper,
        RouteDefinitionLocator routeDefinitionLocator,
        RouteDefinitionWriter routeDefinitionWriter) {
        return new NacosRouteInitializer(
            configService, properties, objectMapper, routeDefinitionLocator, routeDefinitionWriter);
    }
}

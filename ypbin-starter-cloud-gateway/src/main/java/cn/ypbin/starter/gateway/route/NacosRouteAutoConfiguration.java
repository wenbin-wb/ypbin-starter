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
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/**
 * Nacos 动态路由自动配置。
 *
 * <p><b>为何必须有 {@code @AutoConfigureAfter(GatewayAutoConfiguration)}：</b>本类用
 * {@code @ConditionalOnBean({RouteDefinitionLocator.class, RouteDefinitionWriter.class})} 做守卫，
 * 而 {@code @ConditionalOnBean} <b>只看得见「先于它处理完」的 Bean 定义</b>。这两个类型的 Bean
 * 由 Spring Cloud Gateway 的 {@code org.springframework.cloud.gateway.config.GatewayAutoConfiguration}
 * 注册（一手取证：该自动配置的 {@code inMemoryRouteDefinitionRepository()} 提供
 * {@code InMemoryRouteDefinitionRepository}——它同时实现 {@code RouteDefinitionRepository}
 * 接口，而后者继承 {@code RouteDefinitionLocator} 与 {@code RouteDefinitionWriter}；另有
 * {@code propertiesRouteDefinitionLocator()} 提供 {@code RouteDefinitionLocator}，
 * {@code routeDefinitionLocator(List)} 提供主 {@code RouteDefinitionLocator}）。
 * 不加顺序约束时，{@code cn.ypbin.*} 在自动配置排序中排在 {@code org.springframework.*}
 * <b>之前</b>（无显式顺序时按类名字典序）⇒ 条件求值时上述 Bean 尚未注册 ⇒ <b>条件恒假</b> ⇒
 * 宿主设了 {@code ypbin.gateway.route.nacos.enabled=true} 时 {@code NacosRouteInitializer}
 * 整块不装配，Nacos 动态路由<b>静默不生效且无任何报错</b>。</p>
 *
 * <p>这是 2026-10-15 starter v3.9.1 修掉的「{@code @ConditionalOnBean} 判定第三方自动配置提供的
 * 类型导致条件恒假」同一根因；本处由 {@code AutoConfigurationOrderingTest} 门禁覆盖。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
@AutoConfiguration
@AutoConfigureAfter(name = "org.springframework.cloud.gateway.config.GatewayAutoConfiguration")
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

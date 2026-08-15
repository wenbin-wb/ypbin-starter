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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.RouteDefinition;

/**
 * Nacos 动态路由解析测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class NacosRoutePropertiesTest {

    private static final TypeReference<List<RouteDefinition>> ROUTE_LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldHaveSaneDefaults() {
        NacosRouteProperties properties = new NacosRouteProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getDataId()).isEqualTo("gateway-routes.json");
        assertThat(properties.getGroup()).isEqualTo("DEFAULT_GROUP");
        assertThat(properties.getTimeoutMs()).isEqualTo(5000L);
    }

    @Test
    void shouldParseValidRouteDefinitionJson() throws Exception {
        String json = """
            [
              {
                "id": "user-service",
                "uri": "lb://user-service",
                "predicates": [{"name": "Path", "args": {"pattern": "/user/**"}}],
                "filters": [],
                "order": 0
              }
            ]""";

        List<RouteDefinition> routes = objectMapper.readValue(json, ROUTE_LIST_TYPE);

        assertThat(routes).hasSize(1);
        RouteDefinition route = routes.getFirst();
        assertThat(route.getId()).isEqualTo("user-service");
        assertThat(route.getUri().toString()).isEqualTo("lb://user-service");
        assertThat(route.getOrder()).isEqualTo(0);
    }
}

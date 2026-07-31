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

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gateway Swagger 聚合配置。
 *
 * @author wenbin
 * @since 2026-07-31
 */
@ConfigurationProperties(prefix = "ypbin.gateway.swagger")
public class GatewaySwaggerAggregationProperties {

    /** 是否启用 Swagger 聚合，默认关闭 */
    private boolean enabled = false;

    /** 下游服务 /v3/api-docs 路径 */
    private String apiDocsPath = "/v3/api-docs";

    /** 下游服务分组名 */
    private String groupName = "default";

    /** 需要排除的路由 ID 前缀 */
    private List<String> excludedRoutePrefixes = new ArrayList<>(List.of("ReactiveCompositeDiscoveryClient"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiDocsPath() {
        return apiDocsPath;
    }

    public void setApiDocsPath(String apiDocsPath) {
        this.apiDocsPath = apiDocsPath;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public List<String> getExcludedRoutePrefixes() {
        return excludedRoutePrefixes;
    }

    public void setExcludedRoutePrefixes(List<String> excludedRoutePrefixes) {
        this.excludedRoutePrefixes = excludedRoutePrefixes;
    }
}

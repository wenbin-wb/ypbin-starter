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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nacos 动态路由配置项。
 *
 * @author wenbin
 * @since 2026-07-31
 */
@ConfigurationProperties(prefix = NacosRouteProperties.PREFIX)
public class NacosRouteProperties {

    public static final String PREFIX = "ypbin.gateway.route.nacos";

    /** 是否启用 Nacos 动态路由，默认关闭 */
    private boolean enabled = false;

    /** Nacos 配置 Data ID */
    private String dataId = "gateway-routes.json";

    /** Nacos 配置 Group */
    private String group = "DEFAULT_GROUP";

    /** Nacos 读取配置超时（毫秒） */
    private long timeoutMs = 5000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDataId() {
        return dataId;
    }

    public void setDataId(String dataId) {
        this.dataId = dataId;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}

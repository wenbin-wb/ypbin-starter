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
package cn.ypbin.starter.messaging.websocket;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WebSocket（STOMP）配置项。
 *
 * @author wenbin
 * @since 2026-07-30
 */
@ConfigurationProperties(prefix = WebSocketProperties.PREFIX)
public class WebSocketProperties {

    public static final String PREFIX = "ypbin.websocket";

    /** 是否启用 WebSocket，默认关闭（需显式开启） */
    private boolean enabled = false;

    /** STOMP 端点路径 */
    private String endpoint = "/ws";

    /** 客户端订阅目的地前缀 */
    private String applicationPrefix = "/app";

    /** 广播消息目的地前缀 */
    private String brokerPrefix = "/topic";

    /** 允许跨域的来源模式 */
    private String allowedOriginPatterns = "*";

    /** 服务端心跳发送间隔（毫秒），0 表示不发送。用于保活与探测半开连接 */
    private long heartbeatServer = 10_000L;

    /** 期望客户端心跳间隔（毫秒），0 表示不要求 */
    private long heartbeatClient = 10_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getApplicationPrefix() {
        return applicationPrefix;
    }

    public void setApplicationPrefix(String applicationPrefix) {
        this.applicationPrefix = applicationPrefix;
    }

    public String getBrokerPrefix() {
        return brokerPrefix;
    }

    public void setBrokerPrefix(String brokerPrefix) {
        this.brokerPrefix = brokerPrefix;
    }

    public String getAllowedOriginPatterns() {
        return allowedOriginPatterns;
    }

    public void setAllowedOriginPatterns(String allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    public long getHeartbeatServer() {
        return heartbeatServer;
    }

    public void setHeartbeatServer(long heartbeatServer) {
        this.heartbeatServer = heartbeatServer;
    }

    public long getHeartbeatClient() {
        return heartbeatClient;
    }

    public void setHeartbeatClient(long heartbeatClient) {
        this.heartbeatClient = heartbeatClient;
    }
}

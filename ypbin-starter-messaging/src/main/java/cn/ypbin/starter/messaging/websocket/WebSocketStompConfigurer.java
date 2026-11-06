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

import java.util.Arrays;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket STOMP 配置。
 *
 * <p>按 {@link WebSocketProperties} 注册 STOMP 端点与消息代理前缀，提供开箱即用的实时推送。
 * 业务方注入 {@code SimpMessagingTemplate} 即可向客户端广播。需要鉴权 / 拦截时可提供自定义
 * {@link WebSocketMessageBrokerConfigurer} 覆盖。</p>
 *
 * <p>启用服务端心跳（保活 + 探测半开连接）：SimpleBroker 的心跳需配套 {@link TaskScheduler}
 * 才生效，调度器由 Spring 容器管理（见自动配置），生命周期随容器销毁，不会泄漏线程。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class WebSocketStompConfigurer implements WebSocketMessageBrokerConfigurer {

    private final WebSocketProperties properties;
    private final TaskScheduler heartbeatScheduler;

    public WebSocketStompConfigurer(WebSocketProperties properties, TaskScheduler heartbeatScheduler) {
        this.properties = properties;
        this.heartbeatScheduler = heartbeatScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        StompWebSocketEndpointRegistration registration = registry.addEndpoint(properties.getEndpoint());
        String[] patterns = originPatterns();
        if (patterns.length == 0) {
            // 显式传空：仅同源（浏览器同源策略不保护 WebSocket 握手，默认不能放开任意来源）
            registration.setAllowedOriginPatterns();
        } else {
            registration.setAllowedOriginPatterns(patterns);
        }
        // withSockJS() 返回 SockJsServiceRegistration，必须最后调用
        registration.withSockJS();
    }

    /**
     * 解析逗号分隔的来源模式，去空白、丢空项。
     *
     * @return 来源模式数组（空数组 = 仅同源）
     */
    String[] originPatterns() {
        String configured = properties.getAllowedOriginPatterns();
        if (configured == null || configured.isBlank()) {
            return new String[0];
        }
        return Arrays.stream(configured.split(","))
            .map(String::trim)
            .filter(pattern -> !pattern.isEmpty())
            .toArray(String[]::new);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(properties.getBrokerPrefix())
            .setHeartbeatValue(new long[] {properties.getHeartbeatServer(), properties.getHeartbeatClient()})
            .setTaskScheduler(heartbeatScheduler);
        registry.setApplicationDestinationPrefixes(properties.getApplicationPrefix());
    }
}

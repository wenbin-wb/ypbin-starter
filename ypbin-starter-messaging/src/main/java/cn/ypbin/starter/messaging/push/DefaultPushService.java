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
package cn.ypbin.starter.messaging.push;

import cn.ypbin.starter.messaging.sse.SseEmitterManager;

/**
 * 默认推送门面实现（基于 SSE）。
 *
 * <p>把统一推送语义落到 {@link SseEmitterManager}：按用户推送映射到该用户的所有 SSE 连接，广播映射到
 * 全体在线连接。WebSocket 侧由业务方按需通过 {@code SimpMessagingTemplate} 使用，或提供自定义
 * {@link PushService} 覆盖以同时投递多通道。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class DefaultPushService implements PushService {

    private final SseEmitterManager sseManager;

    public DefaultPushService(SseEmitterManager sseManager) {
        this.sseManager = sseManager;
    }

    @Override
    public void sendToUser(String userId, String event, Object data) {
        sseManager.sendToUser(userId, event, data);
    }

    @Override
    public void sendToUser(String userId, PushEvent event) {
        sseManager.sendToUser(userId, event.event(), event.data());
    }

    @Override
    public void broadcast(String event, Object data) {
        sseManager.broadcast(event, data);
    }

    @Override
    public void broadcast(PushEvent event) {
        sseManager.broadcast(event.event(), event.data());
    }

    @Override
    public boolean isOnline(String userId) {
        return sseManager.isOnline(userId);
    }

    @Override
    public int onlineCount() {
        return sseManager.onlineCount();
    }
}

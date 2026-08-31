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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.starter.messaging.sse.SseEmitterManager;
import org.junit.jupiter.api.Test;

/**
 * 推送服务与事件模型测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class PushServiceTest {

    @Test
    void pushEventShouldCarryFields() {
        PushEvent event = PushEvent.of("notice", "hello");
        assertThat(event.event()).isEqualTo("notice");
        assertThat(event.data()).isEqualTo("hello");
    }

    @Test
    void sendToUserShouldDelegateToSseManager() {
        SseEmitterManager manager = mock(SseEmitterManager.class);
        DefaultPushService service = new DefaultPushService(manager);

        service.sendToUser("u1", "message", "data");
        verify(manager).sendToUser("u1", "message", "data");

        PushEvent event = PushEvent.of("notice", "hello");
        service.sendToUser("u1", event);
        verify(manager).sendToUser("u1", "notice", "hello");
    }

    @Test
    void broadcastShouldDelegate() {
        SseEmitterManager manager = mock(SseEmitterManager.class);
        DefaultPushService service = new DefaultPushService(manager);

        service.broadcast("notice", "all");
        service.broadcast(PushEvent.of("notice", "all"));
        verify(manager, org.mockito.Mockito.times(2)).broadcast("notice", "all");
    }

    @Test
    void onlineStatusShouldQueryManager() {
        SseEmitterManager manager = mock(SseEmitterManager.class);
        when(manager.isOnline("u1")).thenReturn(true);
        when(manager.onlineCount()).thenReturn(3);
        DefaultPushService service = new DefaultPushService(manager);

        assertThat(service.isOnline("u1")).isTrue();
        assertThat(service.onlineCount()).isEqualTo(3);
    }

    @Test
    void sendWithEventShouldHandleNullData() {
        SseEmitterManager manager = mock(SseEmitterManager.class);
        DefaultPushService service = new DefaultPushService(manager);
        service.sendToUser("u1", PushEvent.of("ping", null));
        verify(manager).sendToUser(anyString(), anyString(), org.mockito.ArgumentMatchers.isNull());
    }
}

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
package cn.ypbin.starter.messaging.sse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link SseEmitterManager} 单元测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class SseEmitterManagerTest {

    private final SseEmitterManager manager = new SseEmitterManager(60_000L);

    @Test
    void connectShouldRegisterUserOnline() {
        manager.connect("u1");
        assertThat(manager.isOnline("u1")).isTrue();
        assertThat(manager.onlineCount()).isEqualTo(1);
    }

    @Test
    void sameUserMultipleConnectionsCountAsOneUser() {
        manager.connect("u1");
        manager.connect("u1");
        assertThat(manager.onlineCount()).isEqualTo(1);
        assertThat(manager.onlineUsers()).containsExactly("u1");
    }

    @Test
    void sendToOfflineUserShouldNotThrow() {
        // 无连接时推送应静默返回，不抛异常
        manager.sendToUser("nobody", "evt", "data");
        assertThat(manager.isOnline("nobody")).isFalse();
    }

    @Test
    void broadcastWithNoConnectionsShouldNotThrow() {
        manager.broadcast("evt", "data");
        assertThat(manager.onlineCount()).isZero();
    }
}

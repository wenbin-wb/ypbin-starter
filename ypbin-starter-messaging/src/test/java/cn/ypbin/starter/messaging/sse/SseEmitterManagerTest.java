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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * {@link SseEmitterManager} 单元测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class SseEmitterManagerTest {

    /** 现有行为测试：禁用心跳（间隔 0），避免测试期间起调度线程 */
    private final SseEmitterManager manager = new SseEmitterManager(60_000L, 0);

    @Test
    void heartbeatEnabled_whenIntervalPositive() {
        SseEmitterManager m = new SseEmitterManager(60_000L, 30);
        assertThat(m.isHeartbeatEnabled()).isTrue();
    }

    @Test
    void heartbeatDisabled_whenIntervalZero() {
        SseEmitterManager m = new SseEmitterManager(60_000L, 0);
        assertThat(m.isHeartbeatEnabled()).isFalse();
    }

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

    @Test
    void removeWhenNewConnectionAddedConcurrentlyShouldKeepUserOnline() {
        // 直接复现竞态语义：旧连接摘除判空后、清键前，已有新连接加入，则不应删键把新连接变孤儿。
        // 直接调用包级 remove（裸 SseEmitter 的 complete() 不触发回调，无法经真实回调复现）。
        SseEmitter first = manager.connect("A");
        SseEmitter second = manager.connect("A");
        // 摘除两个连接（模拟旧连接陆续断开），期间用户仍应因剩余连接在线
        manager.remove("A", first);
        assertThat(manager.isOnline("A")).isTrue();
        // 摘除最后一个连接后用户离线，键被清理
        manager.remove("A", second);
        assertThat(manager.isOnline("A")).isFalse();
        assertThat(manager.onlineCount()).isZero();
    }

    @Test
    void reconnectAfterFullRemoveShouldBeOnlineAgain() {
        SseEmitter e1 = manager.connect("A");
        manager.remove("A", e1);           // 全部摘除，键清理
        assertThat(manager.isOnline("A")).isFalse();
        manager.connect("A");              // 重新连接
        assertThat(manager.isOnline("A")).isTrue();
    }
}

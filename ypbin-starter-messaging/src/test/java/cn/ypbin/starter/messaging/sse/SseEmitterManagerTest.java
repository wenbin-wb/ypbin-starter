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
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
        assertThat(manager.onlineAudiences()).containsExactly("u1");
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

    /**
     * ★ 宿主自定义 codec 编码时抛异常，不得反向抛给业务发布线程。
     *
     * <p>修前形态：{@code codec.encode(data)} 位于 {@code publishFanout} 的 try/catch 之外，
     * 自定义 codec 的 bug（如懒加载集合在 session 关闭后遍历、`toString` 抛异常）会原样抛回
     * {@code sendToAudience}/{@code broadcast} 的调用方——实时流本是业务写入的旁路，不能反过来
     * 让业务写入失败。修后：与「无法编码」同等处理，WARN + 完整堆栈，且一帧都不发布。</p>
     */
    @Test
    void codecExceptionDuringFanoutPublishDoesNotEscapeToCaller() {
        SseFanoutCodec throwing = new SseFanoutCodec() {

            @Override
            public String id() {
                return "boom-v1";
            }

            @Override
            public Optional<String> encode(Object payload) {
                throw new IllegalStateException("boom-encode");
            }

            @Override
            public Object decode(String text) {
                return text;
            }
        };
        List<SseFanoutMessage> published = new CopyOnWriteArrayList<>();
        SseFanoutBridge bridge = new SseFanoutBridge() {

            @Override
            public void publish(SseFanoutMessage message) {
                published.add(message);
            }

            @Override
            public String instanceId() {
                return "inst-1";
            }
        };
        SseEmitterManager crossInstanceManager = new SseEmitterManager(60_000L, 0, bridge, throwing);

        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(SseEmitterManager.class);
        logger.addAppender(appender);
        try {
            assertThatCode(() -> crossInstanceManager.sendToAudience("tenant:7", "evt", "payload"))
                .doesNotThrowAnyException();
            assertThat(published).as("编码失败时不得发布任何帧（不能把异常编码成垃圾帧）").isEmpty();

            ILoggingEvent warn = appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .findFirst()
                .orElseThrow(() -> new AssertionError("codec 编码异常必须记 WARN"));
            assertThat(warn.getFormattedMessage()).contains("编码异常").contains("tenant:7");
            assertThat(warn.getThrowableProxy())
                .as("自定义 codec 的缺陷必须留下完整堆栈，否则无从定位是哪一帧/哪个载荷")
                .isNotNull();
        } finally {
            logger.detachAppender(appender);
            crossInstanceManager.destroy();
        }
    }
}

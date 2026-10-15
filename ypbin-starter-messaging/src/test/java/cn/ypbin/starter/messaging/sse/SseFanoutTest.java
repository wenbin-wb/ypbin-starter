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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 多实例扇出运行时用例：两个实例共享一条内存"频道"，验证跨实例可达、去自回环、投递隔离与
 * codec 版本防护。
 *
 * <p>为什么必须写这一层：单实例内的 {@code sendToAudience} 只是往内存注册表投递，<strong>跨实例
 * 可达性</strong>（发布 → 频道 → 订阅方 → 本实例连接）与<strong>回环不重复投递</strong>都是
 * "错了也看不出"的语义；用两个 manager 模拟两个 JVM 是最直接的可复现证据。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
class SseFanoutTest {

    /** 内存频道：把发布帧广播给所有订阅者（模拟 Redis Pub/Sub）。 */
    private static final class InMemoryChannel {

        private final List<SseFanoutSubscriber> subscribers = new CopyOnWriteArrayList<>();

        void subscribe(SseFanoutSubscriber subscriber) {
            subscribers.add(subscriber);
        }

        void publish(String frame) {
            Message message = new DefaultMessage("ypbin:sse:fanout".getBytes(StandardCharsets.UTF_8),
                frame.getBytes(StandardCharsets.UTF_8));
            for (SseFanoutSubscriber subscriber : subscribers) {
                subscriber.onMessage(message, null);
            }
        }
    }

    /** 发布桥：把帧写到内存频道（含实例标识，与生产同一协议）。 */
    private record InMemoryBridge(InMemoryChannel channel, String instanceId) implements SseFanoutBridge {

        @Override
        public void publish(SseFanoutMessage message) {
            channel.publish(SseFanoutProtocol.encode(message, instanceId));
        }
    }

    /** 记录「收到了哪些事件」的 emitter 替身（覆写生产管理器 newEmitter 的扩展点）。 */
    private static final class RecordingEmitter extends SseEmitter {

        private final List<Event> events = new CopyOnWriteArrayList<>();

        @Override
        public void send(SseEventBuilder builder) {
            // SseEventBuilderImpl.build() 给出待发送的 SSE 文本片段。只记录带 event: 名的**业务事件**：
            // 建连注释帧（:connected）与心跳（:ping）也走同一路径，若计入会污染「收到了几条事件」的断言。
            for (ResponseBodyEmitter.DataWithMediaType item : builder.build()) {
                String text = String.valueOf(item.getData());
                if (text.contains("event:")) {
                    events.add(new Event(text));
                }
            }
        }

        @Override
        public void send(Object object) {
            events.add(new Event("data=" + object));
        }

        /** 是否收到过包含指定片段的事件。 */
        boolean received(String fragment) {
            return events.stream().anyMatch(event -> event.text.contains(fragment));
        }

        int count() {
            return events.size();
        }

        private record Event(String text) {
        }
    }

    /** 可注入 emitter 的管理器替身：注册/摘除/心跳/扇出全走生产实现，只换 emitter 工厂。 */
    private static final class TestManager extends SseEmitterManager {

        private final RecordingEmitter emitter;

        TestManager(RecordingEmitter emitter) {
            super(60_000L, 0);
            this.emitter = emitter;
        }

        @Override
        SseEmitter newEmitter() {
            return emitter;
        }
    }

    /** 一个"实例"：管理器 + 桥 + 在场连接。 */
    private record Instance(TestManager manager, String instanceId, RecordingEmitter emitter) {
    }

    private static Instance newInstance(InMemoryChannel channel, String instanceId) {
        RecordingEmitter emitter = new RecordingEmitter();
        TestManager manager = new TestManager(emitter);
        manager.setFanoutBridge(new InMemoryBridge(channel, instanceId));
        channel.subscribe(new SseFanoutSubscriber(manager, instanceId, new TextSseFanoutCodec()));
        return new Instance(manager, instanceId, emitter);
    }

    /** 等待异步投递收敛（投递走虚拟线程执行器）。 */
    private static void awaitDelivery() throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            Thread.sleep(20);
        }
    }

    @Test
    void crossInstanceDeliveryReachesConnectionOnOtherInstance() throws InterruptedException {
        InMemoryChannel channel = new InMemoryChannel();
        Instance a = newInstance(channel, "A");
        Instance b = newInstance(channel, "B");
        b.manager().connectAudience("tenant:7");

        // A 上发布：本实例无该 audience 连接，但 B 上有 ⇒ 必须跨实例到达
        a.manager().sendToAudience("tenant:7", "latest", 25);
        awaitDelivery();

        assertThat(b.emitter().received("tenant:7")).isFalse(); // 事件帧不含 audience 文本，只是事件名+数据
        assertThat(b.emitter().count()).isGreaterThan(0);
        assertThat(a.emitter().count()).isZero();
    }

    @Test
    void publisherInstanceDoesNotDuplicateViaItsOwnSubscription() throws InterruptedException {
        InMemoryChannel channel = new InMemoryChannel();
        Instance a = newInstance(channel, "A");
        a.manager().connectAudience("tenant:7");

        a.manager().sendToAudience("tenant:7", "latest", 25);
        awaitDelivery();

        // 本地直投 1 次 + 自广播帧被订阅方丢弃 ⇒ 恰好 1 条，不得 2 条
        assertThat(a.emitter().count()).isEqualTo(1);
    }

    @Test
    void audienceIsRoutingKeyAndOtherAudiencesAreNotDelivered() throws InterruptedException {
        InMemoryChannel channel = new InMemoryChannel();
        Instance b = newInstance(channel, "B");
        b.manager().connectAudience("tenant:7");

        // 推给另一个 audience：B 上虽在线，但不是这个维度 ⇒ 不应收到
        newInstance(channel, "A").manager().sendToAudience("tenant:8", "latest", 25);
        awaitDelivery();

        assertThat(b.emitter().count()).isZero();
    }

    @Test
    void broadcastReachesConnectionsOnAllInstances() throws InterruptedException {
        InMemoryChannel channel = new InMemoryChannel();
        Instance a = newInstance(channel, "A");
        Instance b = newInstance(channel, "B");
        a.manager().connectAudience("tenant:7");
        b.manager().connectAudience("tenant:8");

        a.manager().broadcast("alert", "ack");
        awaitDelivery();

        assertThat(a.emitter().count()).isEqualTo(1);
        assertThat(b.emitter().count()).isEqualTo(1);
    }

    @Test
    void codecMismatchIsDroppedAndNotDelivered() throws InterruptedException {
        InMemoryChannel channel = new InMemoryChannel();
        Instance b = newInstance(channel, "B");
        b.manager().connectAudience("tenant:7");

        // 另一实例以不兼容 codec 版本（text-v2）发布：B 的订阅者 codec 是 text-v1 ⇒ 必须丢弃且不投递
        channel.publish(SseFanoutProtocol.encode(
            new SseFanoutMessage("latest", "tenant:7", "text-v2", "x"), "A"));
        awaitDelivery();

        assertThat(b.emitter().count()).isZero();
    }

    @Test
    void malformedFrameIsIgnoredWithoutThrowing() {
        InMemoryChannel channel = new InMemoryChannel();
        Instance b = newInstance(channel, "B");
        b.manager().connectAudience("tenant:7");

        // 不应抛异常（订阅线程必须存活）
        channel.publish("not-a-valid-frame");
        channel.publish("");
        assertThat(b.emitter().count()).isZero();
    }

    @Test
    void unencodablePayloadStillDeliversLocally() throws InterruptedException {
        InMemoryChannel channel = new InMemoryChannel();
        Instance a = newInstance(channel, "A");
        a.manager().connectAudience("tenant:7");

        // Map 不被默认文本 codec 支持 ⇒ 只投本实例（不静默降级成垃圾帧），本地仍可达
        a.manager().sendToAudience("tenant:7", "latest", new ArrayList<>(List.of("x")));
        awaitDelivery();

        assertThat(a.emitter().count()).isEqualTo(1);
    }

    @Test
    void noBridgeMeansPureSingleInstanceBehaviour() throws InterruptedException {
        // 无 Redis 环境（桥为 null）：本地投递照常，不抛异常
        RecordingEmitter emitter = new RecordingEmitter();
        SseEmitterManager manager = new SseEmitterManager(60_000L, 0);
        manager.setFanoutBridge(null);
        manager.connectAudience("tenant:7");
        manager.sendToAudience("tenant:7", "latest", 1);
        Thread.sleep(100);
        assertThat(emitter.count()).isZero();
        assertThat(manager.onlineAudiences()).containsExactly("tenant:7");
        assertThat(manager.getCodec().id()).isEqualTo(TextSseFanoutCodec.ID);
    }
}

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
 * {@link SseFanoutSubscriber} 的拒绝路径用例（自回环 / 非法帧 / codec 不一致）。
 *
 * <p>用可观测管理器替身断言「投递被调用了几次」，从而把三道拒绝写成可复现的断言，而不是只靠日志。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
class SseFanoutSubscriberTest {

    /** 记录 deliverLocal 调用的管理器替身。 */
    private static class RecordingManager extends SseEmitterManager {

        private int deliveries;

        RecordingManager() {
            super(60_000L, 0);
        }

        @Override
        public void deliverLocal(SseFanoutMessage message, Object data) {
            deliveries++;
        }
    }

    @Test
    void ownFrameIsIgnoredToAvoidSelfLoopback() {
        RecordingManager manager = new RecordingManager();
        SseFanoutSubscriber subscriber = new SseFanoutSubscriber(manager, "A", new TextSseFanoutCodec());
        String own = SseFanoutProtocol.encode(
            new SseFanoutMessage("evt", "tenant:7", TextSseFanoutCodec.ID, "d1"), "A");

        subscriber.onMessage(message(own), null);

        assertThat(manager.deliveries).isZero();
    }

    @Test
    void frameFromAnotherInstanceIsDelivered() {
        RecordingManager manager = new RecordingManager();
        SseFanoutSubscriber subscriber = new SseFanoutSubscriber(manager, "A", new TextSseFanoutCodec());
        String fromOther = SseFanoutProtocol.encode(
            new SseFanoutMessage("evt", "tenant:7", TextSseFanoutCodec.ID, "d1"), "B");

        subscriber.onMessage(message(fromOther), null);

        assertThat(manager.deliveries).isEqualTo(1);
    }

    @Test
    void codecMismatchIsDropped() {
        RecordingManager manager = new RecordingManager();
        SseFanoutSubscriber subscriber = new SseFanoutSubscriber(manager, "A", new TextSseFanoutCodec());
        String otherCodec = SseFanoutProtocol.encode(
            new SseFanoutMessage("evt", "tenant:7", "text-v2", "d1"), "B");

        subscriber.onMessage(message(otherCodec), null);

        assertThat(manager.deliveries).isZero();
    }

    @Test
    void malformedFrameIsDroppedWithoutThrowing() {
        RecordingManager manager = new RecordingManager();
        SseFanoutSubscriber subscriber = new SseFanoutSubscriber(manager, "A", new TextSseFanoutCodec());

        subscriber.onMessage(message("garbage"), null);
        subscriber.onMessage(message(""), null);

        assertThat(manager.deliveries).isZero();
    }

    private static org.springframework.data.redis.connection.Message message(String frame) {
        return new org.springframework.data.redis.connection.DefaultMessage(
            "ypbin:sse:fanout".getBytes(java.nio.charset.StandardCharsets.UTF_8),
            frame.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}

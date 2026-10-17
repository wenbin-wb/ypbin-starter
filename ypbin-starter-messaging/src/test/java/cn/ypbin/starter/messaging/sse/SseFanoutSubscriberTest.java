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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;

/**
 * {@link SseFanoutSubscriber} 的拒绝路径用例（自回环 / 非法帧 / codec 不一致 / 单帧异常隔离）。
 *
 * <p>用可观测管理器替身断言「投递被调用了几次」，从而把各条拒绝写成可复现的断言，而不是只靠日志；
 * 坏帧的 WARN 也直接用 logback {@link ListAppender} 断言，避免「记了没记」靠肉眼。</p>
 *
 * <p>codec 一致性单独钉死：订阅者<strong>不持有</strong>自己的 codec，只用
 * {@link SseEmitterManager#getCodec()}，故这里给管理器换一个自定义 codec，订阅者必须跟着换。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
class SseFanoutSubscriberTest {

    /** 记录 deliverLocal 调用的管理器替身；codec 由构造注入（订阅者必须跟随它）。 */
    private static class RecordingManager extends SseEmitterManager {

        private int deliveries;

        private Object lastData;

        RecordingManager() {
            this(new TextSseFanoutCodec());
        }

        RecordingManager(SseFanoutCodec codec) {
            super(60_000L, 0, null, codec);
        }

        @Override
        public void deliverLocal(SseFanoutMessage message, Object data) {
            if ("boom".equals(message.event())) {
                // 模拟「投递环节抛出受检中断」：验证订阅者不吞中断语义（用 sneakyThrow 绕过方法签名）
                sneakyThrow(new InterruptedException("boom-delivery"));
            }
            deliveries++;
            lastData = data;
        }
    }

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        appender = new ListAppender<>();
        appender.start();
        logger().addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger().detachAppender(appender);
    }

    private static Logger logger() {
        return (Logger) LoggerFactory.getLogger(SseFanoutSubscriber.class);
    }

    private List<ILoggingEvent> warnEvents() {
        return appender.list.stream().filter(event -> event.getLevel() == Level.WARN).toList();
    }

    @Test
    void ownFrameIsIgnoredToAvoidSelfLoopback() {
        RecordingManager manager = new RecordingManager();
        SseFanoutSubscriber subscriber = new SseFanoutSubscriber(manager, "A");
        String own = SseFanoutProtocol.encode(
            new SseFanoutMessage("evt", "tenant:7", TextSseFanoutCodec.ID, "d1"), "A");

        subscriber.onMessage(message(own), null);

        assertThat(manager.deliveries).isZero();
        // 自回环是预期路径：按 debug 记录，不得产生 WARN 噪音
        assertThat(warnEvents()).isEmpty();
    }

    @Test
    void frameFromAnotherInstanceIsDeliveredWithManagerCodec() {
        RecordingManager manager = new RecordingManager();
        SseFanoutSubscriber subscriber = new SseFanoutSubscriber(manager, "A");
        String fromOther = SseFanoutProtocol.encode(
            new SseFanoutMessage("evt", "tenant:7", TextSseFanoutCodec.ID, "d1"), "B");

        subscriber.onMessage(message(fromOther), null);

        assertThat(manager.deliveries).isEqualTo(1);
        // 载荷由管理器持有的 codec 解码：d1 ⇒ BigDecimal（证明解码确实走了同一个 codec）
        assertThat(manager.lastData).isEqualTo(new BigDecimal("1"));
    }

    @Test
    void codecMismatchIsDropped() {
        RecordingManager manager = new RecordingManager();
        SseFanoutSubscriber subscriber = new SseFanoutSubscriber(manager, "A");
        String otherCodec = SseFanoutProtocol.encode(
            new SseFanoutMessage("evt", "tenant:7", "text-v2", "d1"), "B");

        subscriber.onMessage(message(otherCodec), null);

        assertThat(manager.deliveries).isZero();
        assertThat(warnEvents()).hasSize(1);
    }

    @Test
    void malformedFrameIsDroppedWithoutThrowing() {
        RecordingManager manager = new RecordingManager();
        SseFanoutSubscriber subscriber = new SseFanoutSubscriber(manager, "A");

        subscriber.onMessage(message("garbage"), null);
        subscriber.onMessage(message(""), null);

        assertThat(manager.deliveries).isZero();
        assertThat(warnEvents()).hasSize(2);
    }

    /**
     * ★ 单帧异常隔离：codec.decode 抛异常时只丢弃该帧并记 WARN（带完整堆栈），订阅者不向外抛，
     * 后续帧照常处理。
     *
     * <p>修前实测：{@code TextSseFanoutCodec.decode("dNaN")} 抛 {@link NumberFormatException}，
     * {@code SseFanoutSubscriber.onMessage} 未捕获 ⇒ 异常逃逸到 Redis 订阅线程。本用例用「坏载荷才抛」
     * 的 codec 复现该形态，钉死隔离语义。</p>
     */
    @Test
    void decodeFailureIsIsolatedAndLaterFramesAreStillDelivered() {
        SseFanoutCodec flaky = new SseFanoutCodec() {

            private final TextSseFanoutCodec delegate = new TextSseFanoutCodec();

            @Override
            public String id() {
                return TextSseFanoutCodec.ID;
            }

            @Override
            public Optional<String> encode(Object payload) {
                return delegate.encode(payload);
            }

            @Override
            public Object decode(String text) {
                if ("dNaN".equals(text)) {
                    throw new NumberFormatException("boom-decode");
                }
                return delegate.decode(text);
            }
        };
        RecordingManager manager = new RecordingManager(flaky);
        SseFanoutSubscriber subscriber = new SseFanoutSubscriber(manager, "A");

        String badFrame = SseFanoutProtocol.encode(
            new SseFanoutMessage("evt", "tenant:7", TextSseFanoutCodec.ID, "dNaN"), "B");
        String goodFrame = SseFanoutProtocol.encode(
            new SseFanoutMessage("evt", "tenant:7", TextSseFanoutCodec.ID, "d7"), "B");

        // 订阅线程不得被单帧异常打断
        assertThatCode(() -> subscriber.onMessage(message(badFrame), null)).doesNotThrowAnyException();
        assertThat(manager.deliveries).isZero();
        assertThat(warnEvents()).singleElement().satisfies(event -> {
            assertThat(event.getFormattedMessage()).contains("处理 SSE 扇出帧失败");
            // 必须带完整堆栈，便于定位是哪个载荷/哪一帧
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getThrowableProxy().getClassName()).isEqualTo(NumberFormatException.class.getName());
        });

        // 后续帧照常投递：坏帧只影响它自己
        subscriber.onMessage(message(goodFrame), null);
        assertThat(manager.deliveries).isEqualTo(1);
        assertThat(manager.lastData).isEqualTo(new BigDecimal("7"));
    }

    /**
     * ★ 中断语义不被吞：投递环节抛出 {@link InterruptedException} 时，订阅者丢弃该帧、记 WARN，
     * 并把中断标志位恢复给上层（而不是 catch 掉就完事）。
     */
    @Test
    void interruptionFromDeliveryIsNotSwallowed() {
        RecordingManager manager = new RecordingManager();
        SseFanoutSubscriber subscriber = new SseFanoutSubscriber(manager, "A");
        String frame = SseFanoutProtocol.encode(
            new SseFanoutMessage("boom", "tenant:7", TextSseFanoutCodec.ID, "d1"), "B");

        try {
            assertThatCode(() -> subscriber.onMessage(message(frame), null)).doesNotThrowAnyException();
            assertThat(Thread.currentThread().isInterrupted())
                .as("中断标志位必须被恢复，供监听容器/线程池感知")
                .isTrue();
            assertThat(manager.deliveries).isZero();
            assertThat(warnEvents()).hasSize(1);
        } finally {
            // 清掉标志位，避免污染 surefire 的测试线程（后续用例可能依赖可中断的等待）
            Thread.interrupted();
        }
    }

    /**
     * ★ codec 一致性（结构上不可能不一致）：订阅者只用 {@link SseEmitterManager#getCodec()}，
     * 管理器的 codec 换成 {@code custom-v1} 后，订阅者必须按 {@code custom-v1} 收帧、按 {@code text-v1}
     * 拒帧——这正是「宿主自定义管理器」场景下的保护点。
     */
    @Test
    void subscriberAlwaysFollowsManagerCodec() {
        SseFanoutCodec custom = new SseFanoutCodec() {

            @Override
            public String id() {
                return "custom-v1";
            }

            @Override
            public Optional<String> encode(Object payload) {
                return Optional.of("custom");
            }

            @Override
            public Object decode(String text) {
                return "decoded:" + text;
            }
        };
        RecordingManager manager = new RecordingManager(custom);
        SseFanoutSubscriber subscriber = new SseFanoutSubscriber(manager, "A");

        String customFrame = SseFanoutProtocol.encode(
            new SseFanoutMessage("evt", "tenant:7", "custom-v1", "payload"), "B");
        String textFrame = SseFanoutProtocol.encode(
            new SseFanoutMessage("evt", "tenant:7", TextSseFanoutCodec.ID, "d1"), "B");

        subscriber.onMessage(message(customFrame), null);
        subscriber.onMessage(message(textFrame), null);

        assertThat(manager.deliveries).isEqualTo(1);
        assertThat(manager.lastData).isEqualTo("decoded:payload");
        assertThat(warnEvents()).hasSize(1);
    }

    /**
     * ★ 坏消息的「记录」本身不得成为新的异常：频道为 {@code null} 时 WARN 里退化为占位符。
     *
     * <p>修前形态：WARN 直接用 {@code new String(message.getChannel(), UTF_8)} 拼频道名，频道为
     * {@code null} 时 {@link NullPointerException} 从 catch 块里抛出、逃逸到 Redis 订阅线程——
     * 坏帧反而把订阅线程打挂，且连「哪一帧坏了」都没记下来。</p>
     */
    @Test
    void nullChannelDoesNotMakeFailureLoggingThrow() {
        RecordingManager manager = new RecordingManager();
        SseFanoutSubscriber subscriber = new SseFanoutSubscriber(manager, "A");
        Message noChannel = mock(Message.class);
        when(noChannel.getBody()).thenReturn("garbage".getBytes(StandardCharsets.UTF_8));
        // channel 保持 Mockito 默认 null

        assertThatCode(() -> subscriber.onMessage(noChannel, null)).doesNotThrowAnyException();

        assertThat(manager.deliveries).isZero();
        assertThat(warnEvents()).singleElement().satisfies(event ->
            assertThat(event.getFormattedMessage()).contains("<unknown>"));
    }

    /** ★ body 为 {@code null} 的消息（第三方/测试替身可能给出）只丢弃并 WARN，不抛 NPE。 */
    @Test
    void nullBodyIsDroppedWithoutThrowing() {
        RecordingManager manager = new RecordingManager();
        SseFanoutSubscriber subscriber = new SseFanoutSubscriber(manager, "A");
        Message noBody = mock(Message.class);
        when(noBody.getChannel()).thenReturn("ypbin:sse:fanout".getBytes(StandardCharsets.UTF_8));
        // body 保持 Mockito 默认 null

        assertThatCode(() -> subscriber.onMessage(noBody, null)).doesNotThrowAnyException();

        assertThat(manager.deliveries).isZero();
        assertThat(warnEvents()).singleElement().satisfies(event ->
            assertThat(event.getFormattedMessage()).contains("body 为 null"));
    }

    /** 抛出受检异常而无需在签名里声明（测试专用，用于复现「投递环节中断」）。 */
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneakyThrow(Throwable throwable) throws T {
        throw (T) throwable;
    }

    private static org.springframework.data.redis.connection.Message message(String frame) {
        return new org.springframework.data.redis.connection.DefaultMessage(
            "ypbin:sse:fanout".getBytes(StandardCharsets.UTF_8),
            frame.getBytes(StandardCharsets.UTF_8));
    }
}

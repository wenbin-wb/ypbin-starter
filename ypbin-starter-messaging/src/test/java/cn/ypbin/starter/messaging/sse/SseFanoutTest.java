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

import cn.ypbin.starter.messaging.autoconfigure.SseAutoConfiguration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/**
 * SSE 多实例扇出运行时用例：两个实例共享一条内存"频道"，验证跨实例可达、去自回环、投递隔离、
 * codec 版本防护，以及「管理器与订阅者用同一个 codec」这条装配约束。
 *
 * <p>为什么必须写这一层：单实例内的 {@code sendToAudience} 只是往内存注册表投递，<strong>跨实例
 * 可达性</strong>（发布 → 频道 → 订阅方 → 本实例连接）与<strong>回环不重复投递</strong>都是
 * "错了也看不出"的语义；用两个实例模拟两个 JVM 是最直接的可复现证据。</p>
 *
 * <p><strong>为什么经 Spring 容器装配（2026-10-17 起）</strong>：此前用例自行 {@code new} 管理器与
 * 订阅者，于是「装配接线」本身没有端到端保护——独立验收实测，只要宿主自定义
 * {@link SseEmitterManager}，管理器与订阅者就可能拿到不同 codec，帧里的 codecId 对不上、跨实例帧被
 * <strong>整批丢弃</strong>。现在每个「实例」都由
 * {@link WebApplicationContextRunner} 走 {@link SseAutoConfiguration} 真实装配（管理器与订阅者都从容器
 * 里取），该约束因此被端到端钉死。</p>
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

        /**
         * 已投递的 <strong>data 项</strong>原文。
         *
         * <p>{@code SseEventBuilderImpl.build()} 会把「事件名/注释文本」与「data 对象」拆成不同条目：
         * 前者带 {@code text/plain} 媒体类型，后者（未显式指定媒体类型）为 {@code null}。只记录后者，
         * 才能断言「跨实例到底投出去的是什么载荷」。</p>
         */
        private final List<Object> payloads = new CopyOnWriteArrayList<>();

        @Override
        public void send(SseEventBuilder builder) {
            // SseEventBuilderImpl.build() 给出待发送的 SSE 文本片段。只记录带 event: 名的**业务事件**：
            // 建连注释帧（:connected）与心跳（:ping）也走同一路径，若计入会污染「收到了几条事件」的断言。
            for (ResponseBodyEmitter.DataWithMediaType item : builder.build()) {
                if (item.getMediaType() == null) {
                    payloads.add(item.getData());
                    continue;
                }
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

        /** 已投递的数据项（保持投递时的原始对象/字符串形态）。 */
        List<Object> payloads() {
            return payloads;
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

        TestManager(RecordingEmitter emitter, SseFanoutCodec codec) {
            super(60_000L, 0, null, codec);
            this.emitter = emitter;
        }

        @Override
        SseEmitter newEmitter() {
            return emitter;
        }
    }

    /** 一个"实例"：管理器 + 订阅者 + 在场连接（前两者均由 Spring 容器装配）。 */
    private record Instance(SseEmitterManager manager, SseFanoutSubscriber subscriber, RecordingEmitter emitter) {
    }

    /**
     * 宿主自定义管理器，但 codec 取<strong>容器选中的</strong>那个（与生产 {@code sseEmitterManager}
     * 的取值方式一致）：验证默认装配路径下发布侧与订阅侧同源。
     */
    @Configuration(proxyBeanMethods = false)
    static class ContainerCodecManagerConfig {

        @Bean
        SseEmitterManager sseEmitterManager(RecordingEmitter emitter, SseFanoutCodec codec) {
            return new TestManager(emitter, codec);
        }

        @Bean
        SseFanoutBridge sseFanoutBridge(InMemoryChannel channel, SseEmitterManager manager) {
            return newInMemoryBridge(channel, manager);
        }
    }

    /**
     * 宿主自定义管理器且<strong>自带</strong> codec（与容器里的 {@link SseFanoutCodec} Bean 不同）：
     * 订阅者必须跟随管理器，否则跨实例帧会被整批丢弃。
     */
    @Configuration(proxyBeanMethods = false)
    static class OwnCodecManagerConfig {

        @Bean
        SseEmitterManager sseEmitterManager(RecordingEmitter emitter) {
            return new TestManager(emitter, new TextSseFanoutCodec());
        }

        @Bean
        SseFanoutBridge sseFanoutBridge(InMemoryChannel channel, SseEmitterManager manager) {
            return newInMemoryBridge(channel, manager);
        }
    }

    /** 宿主自备扇出桥：与自动配置的 Redis 桥同接口、同一回填点（{@code setFanoutBridge}）。 */
    private static SseFanoutBridge newInMemoryBridge(InMemoryChannel channel, SseEmitterManager manager) {
        InMemoryBridge bridge = new InMemoryBridge(channel, UUID.randomUUID().toString());
        manager.setFanoutBridge(bridge);
        return bridge;
    }

    /**
     * 一个「实例」的容器 runner：真实走 {@link SseAutoConfiguration} 装配，管理器与订阅者都从容器里取。
     *
     * @param channel      内存频道（宿主自备桥的传输）
     * @param emitter      连接替身（宿主自定义管理器注入）
     * @param codec        宿主自定义 codec；{@code null} 表示用容器自动选择的（Text 或 Jackson）
     * @param objectMapper 容器里的 Jackson 序列化器；{@code null} 表示不提供（走 Text 兜底）
     * @param managerConfig 宿主管理器/桥配置（决定管理器自带还是取容器 codec）
     */
    private static WebApplicationContextRunner instanceRunner(InMemoryChannel channel, RecordingEmitter emitter,
                                                             @Nullable SseFanoutCodec codec,
                                                             @Nullable ObjectMapper objectMapper,
                                                             Class<?> managerConfig) {
        WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SseAutoConfiguration.class))
            .withPropertyValues("ypbin.sse.enabled=true")
            .withBean(InMemoryChannel.class, () -> channel)
            .withBean(RecordingEmitter.class, () -> emitter)
            // 宿主自备管理器/桥时，仍需 StringRedisTemplate 才会展开 Redis 扇出配置（订阅者 Bean 在其中）。
            // 订阅容器用桩：真实容器在假连接上 start() 会订阅超时，把上下文带崩（与 SseAutoConfigurationTest 同一取舍）。
            .withBean(StringRedisTemplate.class, () -> Mockito.mock(StringRedisTemplate.class))
            .withBean(RedisMessageListenerContainer.class, () -> Mockito.mock(RedisMessageListenerContainer.class))
            .withUserConfiguration(managerConfig);
        if (codec != null) {
            runner = runner.withBean(SseFanoutCodec.class, () -> codec);
        }
        if (objectMapper != null) {
            runner = runner.withBean(ObjectMapper.class, () -> objectMapper);
        }
        return runner;
    }

    /** 在容器存活期间对单个实例做断言的回调。 */
    @FunctionalInterface
    private interface InstanceBody {
        void accept(Instance instance) throws Throwable;
    }

    /** 在两个容器同时存活期间做断言的回调。 */
    @FunctionalInterface
    private interface TwoInstanceBody {
        void accept(Instance a, Instance b) throws Throwable;
    }

    /** 经容器装配一个实例、把它挂到内存频道上，并在回调期间保持上下文存活。 */
    private static void withInstance(InMemoryChannel channel, @Nullable SseFanoutCodec codec,
                                     @Nullable ObjectMapper objectMapper, Class<?> managerConfig,
                                     InstanceBody body) throws Throwable {
        RecordingEmitter emitter = new RecordingEmitter();
        instanceRunner(channel, emitter, codec, objectMapper, managerConfig).run(context -> {
            assertThat(context).hasNotFailed();
            Instance instance = new Instance(context.getBean(SseEmitterManager.class),
                context.getBean(SseFanoutSubscriber.class), emitter);
            channel.subscribe(instance.subscriber());
            body.accept(instance);
        });
    }

    /** 同时装配两个实例（嵌套上下文，两者在回调期间均存活）。 */
    private static void withTwoInstances(InMemoryChannel channel, @Nullable SseFanoutCodec codec,
                                         @Nullable ObjectMapper objectMapper, Class<?> managerConfig,
                                         TwoInstanceBody body) throws Throwable {
        withInstance(channel, codec, objectMapper, managerConfig,
            a -> withInstance(channel, codec, objectMapper, managerConfig, b -> body.accept(a, b)));
    }

    /** 等待异步投递收敛（投递走虚拟线程执行器）。 */
    private static void awaitDelivery() throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            Thread.sleep(20);
        }
    }

    @Test
    void crossInstanceDeliveryReachesConnectionOnOtherInstance() throws Throwable {
        InMemoryChannel channel = new InMemoryChannel();
        withTwoInstances(channel, null, null, ContainerCodecManagerConfig.class, (a, b) -> {
            b.manager().connectAudience("tenant:7");

            // A 上发布：本实例无该 audience 连接，但 B 上有 ⇒ 必须跨实例到达
            a.manager().sendToAudience("tenant:7", "latest", 25);
            awaitDelivery();

            assertThat(b.emitter().received("tenant:7")).isFalse(); // 事件帧不含 audience 文本，只是事件名+数据
            assertThat(b.emitter().count()).isGreaterThan(0);
            assertThat(a.emitter().count()).isZero();
        });
    }

    @Test
    void publisherInstanceDoesNotDuplicateViaItsOwnSubscription() throws Throwable {
        InMemoryChannel channel = new InMemoryChannel();
        withInstance(channel, null, null, ContainerCodecManagerConfig.class, a -> {
            a.manager().connectAudience("tenant:7");

            a.manager().sendToAudience("tenant:7", "latest", 25);
            awaitDelivery();

            // 本地直投 1 次 + 自广播帧被订阅方丢弃 ⇒ 恰好 1 条，不得 2 条
            assertThat(a.emitter().count()).isEqualTo(1);
        });
    }

    @Test
    void audienceIsRoutingKeyAndOtherAudiencesAreNotDelivered() throws Throwable {
        InMemoryChannel channel = new InMemoryChannel();
        withInstance(channel, null, null, ContainerCodecManagerConfig.class, b -> {
            b.manager().connectAudience("tenant:7");

            // 推给另一个 audience：B 上虽在线，但不是这个维度 ⇒ 不应收到
            withInstance(channel, null, null, ContainerCodecManagerConfig.class, a -> {
                a.manager().sendToAudience("tenant:8", "latest", 25);
                awaitDelivery();
            });

            assertThat(b.emitter().count()).isZero();
        });
    }

    @Test
    void broadcastReachesConnectionsOnAllInstances() throws Throwable {
        InMemoryChannel channel = new InMemoryChannel();
        withTwoInstances(channel, null, null, ContainerCodecManagerConfig.class, (a, b) -> {
            a.manager().connectAudience("tenant:7");
            b.manager().connectAudience("tenant:8");

            a.manager().broadcast("alert", "ack");
            awaitDelivery();

            assertThat(a.emitter().count()).isEqualTo(1);
            assertThat(b.emitter().count()).isEqualTo(1);
        });
    }

    @Test
    void codecMismatchIsDroppedAndNotDelivered() throws Throwable {
        InMemoryChannel channel = new InMemoryChannel();
        withInstance(channel, null, null, ContainerCodecManagerConfig.class, b -> {
            b.manager().connectAudience("tenant:7");

            // 另一实例以不兼容 codec 版本（text-v2）发布：B 的订阅者 codec 是 text-v1 ⇒ 必须丢弃且不投递
            channel.publish(SseFanoutProtocol.encode(
                new SseFanoutMessage("latest", "tenant:7", "text-v2", "x"), "A"));
            awaitDelivery();

            assertThat(b.emitter().count()).isZero();
        });
    }

    @Test
    void malformedFrameIsIgnoredWithoutThrowing() throws Throwable {
        InMemoryChannel channel = new InMemoryChannel();
        withInstance(channel, null, null, ContainerCodecManagerConfig.class, b -> {
            b.manager().connectAudience("tenant:7");

            // 不应抛异常（订阅线程必须存活）
            channel.publish("not-a-valid-frame");
            channel.publish("");
            assertThat(b.emitter().count()).isZero();
        });
    }

    @Test
    void unencodablePayloadStillDeliversLocally() throws Throwable {
        InMemoryChannel channel = new InMemoryChannel();
        withInstance(channel, null, null, ContainerCodecManagerConfig.class, a -> {
            a.manager().connectAudience("tenant:7");

            // Map 不被默认文本 codec 支持 ⇒ 只投本实例（不静默降级成垃圾帧），本地仍可达
            a.manager().sendToAudience("tenant:7", "latest", new ArrayList<>(List.of("x")));
            awaitDelivery();

            assertThat(a.emitter().count()).isEqualTo(1);
        });
    }

    @Test
    void noBridgeMeansPureSingleInstanceBehaviour() throws InterruptedException {
        // 无 Redis 环境（桥为 null）：本地投递照常，不抛异常。此路径不经容器（没有桥就没有扇出装配）
        RecordingEmitter emitter = new RecordingEmitter();
        SseEmitterManager manager = new TestManager(emitter, new TextSseFanoutCodec());
        manager.setFanoutBridge(null);
        manager.connectAudience("tenant:7");
        manager.sendToAudience("tenant:7", "latest", 1);
        awaitDelivery();
        assertThat(emitter.count()).isEqualTo(1);
        assertThat(manager.onlineAudiences()).containsExactly("tenant:7");
        assertThat(manager.getCodec().id()).isEqualTo(TextSseFanoutCodec.ID);
    }

    /**
     * ★ 装配接线端到端保护：宿主自定义 {@link SseEmitterManager} 且其 codec 与容器里的
     * {@link SseFanoutCodec} Bean <strong>不同</strong>时，订阅者必须跟随<strong>管理器</strong>的 codec。
     *
     * <p>修前形态：{@code SseFanoutSubscriber} 持有由容器注入的 codec（此处为 {@code custom-v1}），
     * 而管理器自带 {@code text-v1} ⇒ 发布侧写 {@code text-v1}、订阅侧比对 {@code custom-v1} ⇒
     * <strong>整批跨实例帧被丢弃</strong>（多副本下 SSE 静默失效）。现在订阅者只用
     * {@link SseEmitterManager#getCodec()}，两侧结构上同源，故本用例必须送达。</p>
     */
    @Test
    void customManagerCodecOverridesContainerCodecBeanWithoutBreakingFanout() throws Throwable {
        SseFanoutCodec containerCodec = new SseFanoutCodec() {

            @Override
            public String id() {
                return "custom-v1";
            }

            @Override
            public Optional<String> encode(Object payload) {
                return Optional.empty();
            }

            @Override
            public Object decode(String text) {
                return text;
            }
        };
        InMemoryChannel channel = new InMemoryChannel();
        withTwoInstances(channel, containerCodec, null, OwnCodecManagerConfig.class, (a, b) -> {
            b.manager().connectAudience("tenant:7");

            a.manager().sendToAudience("tenant:7", "latest", 25);
            awaitDelivery();

            assertThat(a.manager().getCodec().id()).as("前置条件：管理器自带 text-v1").isEqualTo(TextSseFanoutCodec.ID);
            assertThat(b.manager().getCodec().id()).isEqualTo(TextSseFanoutCodec.ID);
            assertThat(b.emitter().count())
                .as("订阅者必须用管理器（text-v1）而非容器 Bean（custom-v1）的 codec，否则帧被整批丢弃")
                .isEqualTo(1);
        });
    }

    /**
     * issue #97 端到端：结构化对象（record）在 Jackson codec 下<strong>必须真正跨实例到达</strong>。
     *
     * <p>修前（只有 {@link TextSseFanoutCodec}）该载荷 {@code encode} 为空 ⇒ 发布方只投本实例，
     * B 永远收不到；本用例用两个实例模拟两个 JVM，直接钉死「其它节点的订阅者也收得到」。
     * B 收到的是 JSON <strong>文本</strong>，由投递方作为 {@code data:} 原文发出（前端自行 JSON.parse）。
     * 这里经容器装配（容器里有 {@code ObjectMapper} Bean ⇒ 自动选中 Jackson codec），管理器与订阅者
     * 用的都是它。</p>
     */
    @Test
    void structuredPayloadFansOutAcrossInstancesWithJacksonCodec() throws Throwable {
        ObjectMapper objectMapper = new ObjectMapper();
        InMemoryChannel channel = new InMemoryChannel();
        withTwoInstances(channel, null, objectMapper, ContainerCodecManagerConfig.class, (a, b) -> {
            a.manager().connectAudience("tenant:7");
            b.manager().connectAudience("tenant:7");

            a.manager().sendToAudience("tenant:7", "latest", new DeviceReading("d1", 25, List.of("t1", "t2")));
            awaitDelivery();

            assertThat(a.manager().getCodec().id()).isEqualTo(JacksonSseFanoutCodec.ID);
            assertThat(b.manager().getCodec().id()).isEqualTo(JacksonSseFanoutCodec.ID);
            assertThat(b.emitter().count()).as("其它实例的订阅者必须收到结构化事件（修前为 0）").isEqualTo(1);
            assertThat(b.emitter().payloads()).as("跨实例投递的载荷必须是可 JSON.parse 的文本").hasSize(1);
            String delivered = String.valueOf(b.emitter().payloads().get(0));
            assertThat(objectMapper.readTree(delivered).get("deviceId").asString()).isEqualTo("d1");
            assertThat(objectMapper.readTree(delivered).get("value").asInt()).isEqualTo(25);
            assertThat(objectMapper.readTree(delivered).get("tags").size()).isEqualTo(2);
            // 发布实例本机直投 1 次且不经 codec（仍是原始对象）；自广播帧被订阅方丢弃 ⇒ 不得重复
            assertThat(a.emitter().count()).isEqualTo(1);
            assertThat(a.emitter().payloads()).hasSize(1);
            assertThat(a.emitter().payloads().get(0)).isInstanceOf(DeviceReading.class);
        });
    }

    /** 跨实例投递的典型业务载荷。 */
    private record DeviceReading(String deviceId, int value, List<String> tags) {
    }
}

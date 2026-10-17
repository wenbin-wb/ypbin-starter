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
import cn.ypbin.starter.test.condition.EnabledIfRedisAvailable;
import cn.ypbin.starter.test.container.RedisIntegrationTestSupport;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 跨实例扇出的<strong>真实 Redis（Pub/Sub）</strong>集成测试。
 *
 * <p><b>为什么必须有这一层</b>：既有 {@code SseFanoutTest} 用内存频道模拟传输，能证明协议与
 * 「订阅者跟随管理器 codec」等接线，但证明不了<strong>真实 Redis 传输</strong>下的关键语义：</p>
 * <ol>
 *   <li><strong>订阅真的建立</strong>：{@link RedisMessageListenerContainer} 由自动配置装配并随容器生命周期
 *       启动，帧用 {@code StringRedisTemplate#convertAndSend} 经序列化后进入频道——任一环节错位（频道名
 *       不一致、序列化改编码、容器未启动）都会表现为「跨实例永远收不到」，而内存频道测试看不出来；</li>
 *   <li><strong>自广播回环真的被丢弃</strong>：发布实例的本地连接只应收到 1 条（本地直投），若 instanceId
 *       在发布器与订阅者之间不一致，同一帧会经 Redis 回来再投一次（客户端收到重复事件）；</li>
 *   <li><strong>跨实例真的能收到</strong>：连接在 B 实例、推送在 A 实例发生时，B 的连接必须收到事件。</li>
 * </ol>
 *
 * <p>Redis 由共享测试基座提供：外部实例优先，否则 Testcontainers 拉起 {@code redis:7-alpine}；两者都不可用
 * 时由 {@link EnabledIfRedisAvailable} <strong>显式跳过</strong>（记为 skipped，而非静默通过）。仅在
 * {@code mvn -Pit verify} 下执行。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
@EnabledIfRedisAvailable
class SseFanoutRedisIT {

    /** 本用例独占的扇出频道：避免与其它真实 Redis 使用者/残留订阅互相投递。 */
    private static final String FANOUT_CHANNEL = "ypbin:sse:fanout:it:" + UUID.randomUUID();

    /** 扇出目标键（连接在 B 实例、推送发生在 A 实例）。 */
    private static final String AUDIENCE = "tenant:7";

    /** 断言投递内容用的载荷标记。 */
    private static final String PAYLOAD = "cross-instance-redis-payload";

    /** 跨实例投递的收敛等待上限（毫秒）；Pub/Sub 是异步投递，不能假设 publish 返回即已送达。 */
    private static final long DELIVERY_TIMEOUT_MILLIS = 10_000L;

    /** 轮询间隔（毫秒）。 */
    private static final long POLL_INTERVAL_MILLIS = 50L;

    @Test
    @DisplayName("真实 Redis：A 实例推送 → B 实例连接收到；A 的本地连接不因自广播帧重复收到")
    void crossInstanceFanoutOverRealRedisDeliversOnceAndDropsSelfLoopback() {
        RecordingEmitter emitterA = new RecordingEmitter();
        RecordingEmitter emitterB = new RecordingEmitter();

        runner(emitterA).run(a -> {
            assertThat(a).as("A 实例（发布方）容器应正常启动").hasNotFailed();
            runner(emitterB).run(b -> {
                assertThat(b).as("B 实例（接收方）容器应正常启动").hasNotFailed();

                SseFanoutBridge bridgeA = a.getBean(SseFanoutBridge.class);
                SseFanoutBridge bridgeB = b.getBean(SseFanoutBridge.class);
                assertThat(bridgeA)
                    .as("真实 Redis 在场时自动配置必须装配 Redis 发布器，而不是退化为单实例")
                    .isInstanceOf(RedisSseFanoutPublisher.class);
                assertThat(((RedisSseFanoutPublisher) bridgeA).getChannel())
                    .as("发布器必须使用配置的扇出频道")
                    .isEqualTo(FANOUT_CHANNEL);
                assertThat(bridgeA.instanceId())
                    .as("两个实例的扇出标识必须不同，否则对端会把自己的帧当回环丢掉")
                    .isNotEqualTo(bridgeB.instanceId());
                assertThat(a.getBean(RedisMessageListenerContainer.class).isRunning())
                    .as("订阅容器必须已启动（否则「收不到」会被误判成业务问题）")
                    .isTrue();

                SseEmitterManager managerA = a.getBean(SseEmitterManager.class);
                SseEmitterManager managerB = b.getBean(SseEmitterManager.class);
                managerA.connectAudience(AUDIENCE);
                managerB.connectAudience(AUDIENCE);

                // Pub/Sub 在订阅建立前发布的消息会永久丢失：先用探针事件重试，直到观测到跨实例投递
                awaitCrossInstanceReady(managerA, emitterB);

                // 清掉探针流量，只断言真正的一次业务推送
                emitterA.clear();
                emitterB.clear();
                managerA.sendToAudience(AUDIENCE, "latest", PAYLOAD);

                awaitPayload(emitterB, PAYLOAD);
                // 再等一个收敛窗口：若存在自广播回环，重复帧会在这段时间内到达
                sleep(500L);

                assertThat(emitterB.countContaining(PAYLOAD))
                    .as("B 实例（连接所在实例）必须收到 A 发布的事件，且只收到一次")
                    .isEqualTo(1);
                assertThat(emitterA.countContaining(PAYLOAD))
                    .as("本地直投 1 次 + 自广播帧被订阅方丢弃 ⇒ 恰好 1 条；出现 2 条说明回环去重失效")
                    .isEqualTo(1);
            });
        });
    }

    /**
     * 构造一个「实例」的容器 runner：真实 Redis 连接 + 自动配置的发布器/订阅者/订阅容器，
     * 只把 {@link SseEmitterManager} 换成可观测的替身（生产路径其余部分全部保留）。
     *
     * @param emitter 该实例的连接替身（记录投递内容）
     * @return 容器 runner
     */
    private static WebApplicationContextRunner runner(RecordingEmitter emitter) {
        RedisConnectionFactory connectionFactory = RedisIntegrationTestSupport.connectionFactory();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        return new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SseAutoConfiguration.class))
            .withPropertyValues(
                "ypbin.sse.enabled=true",
                "ypbin.sse.fanout-channel=" + FANOUT_CHANNEL)
            .withBean(RedisConnectionFactory.class, () -> connectionFactory)
            .withBean(StringRedisTemplate.class, () -> redisTemplate)
            .withBean(RecordingEmitter.class, () -> emitter)
            .withUserConfiguration(RecordingManagerConfig.class);
    }

    /**
     * 等待跨实例订阅真正建立：反复发送探针事件，直到接收实例的连接观测到任意一条。
     *
     * <p>不能只 sleep 固定时长——订阅容器启动是异步的，固定等待在慢机器上会假失败、在快机器上白等。</p>
     *
     * @param publisher 发布方管理器
     * @param receiver  接收方的连接替身
     */
    private static void awaitCrossInstanceReady(SseEmitterManager publisher, RecordingEmitter receiver) {
        long deadline = System.currentTimeMillis() + DELIVERY_TIMEOUT_MILLIS;
        int probe = 0;
        while (System.currentTimeMillis() < deadline) {
            publisher.sendToAudience(AUDIENCE, "fanout-probe", "probe-" + probe++);
            sleep(POLL_INTERVAL_MILLIS);
            if (receiver.countContaining("probe-") > 0) {
                return;
            }
        }
        throw new AssertionError("10s 内未观测到任何跨实例投递：请检查真实 Redis 订阅是否建立");
    }

    /**
     * 等待接收方收到指定载荷（异步投递 + Redis 往返，必须轮询）。
     *
     * @param receiver 接收方连接替身
     * @param payload  期望载荷标记
     */
    private static void awaitPayload(RecordingEmitter receiver, String payload) {
        long deadline = System.currentTimeMillis() + DELIVERY_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (receiver.countContaining(payload) > 0) {
                return;
            }
            sleep(POLL_INTERVAL_MILLIS);
        }
        throw new AssertionError("10s 内未收到跨实例载荷：" + payload + "（实际投递：" + receiver.items() + "）");
    }

    /**
     * 超时等待（不依赖 Awaitility）。
     *
     * @param millis 等待毫秒数
     */
    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待跨实例投递时被中断", ex);
        }
    }

    /** 宿主自定义管理器配置：只替换 emitter 工厂，其余（发布器/订阅者/订阅容器）全部走自动配置。 */
    @Configuration(proxyBeanMethods = false)
    static class RecordingManagerConfig {

        @Bean
        SseEmitterManager sseEmitterManager(RecordingEmitter emitter) {
            return new RecordingManager(emitter);
        }
    }

    /** 把 {@code newEmitter()} 换成记录型替身的管理器（包级扩展点，不改变生产语义）。 */
    private static final class RecordingManager extends SseEmitterManager {

        private final RecordingEmitter emitter;

        private RecordingManager(RecordingEmitter emitter) {
            super(60_000L, 0, null, new TextSseFanoutCodec());
            this.emitter = emitter;
        }

        @Override
        SseEmitter newEmitter() {
            return emitter;
        }
    }

    /** 记录所有投递条目的连接替身（事件名与 data 项都按原文记录，便于断言载荷确实到达）。 */
    private static final class RecordingEmitter extends SseEmitter {

        private final List<String> items = new CopyOnWriteArrayList<>();

        @Override
        public void send(SseEventBuilder builder) {
            for (ResponseBodyEmitter.DataWithMediaType item : builder.build()) {
                items.add(String.valueOf(item.getData()));
            }
        }

        @Override
        public void send(Object object) {
            items.add(String.valueOf(object));
        }

        /** 清空已记录投递（用于丢弃探针流量）。 */
        void clear() {
            items.clear();
        }

        /** 包含指定片段的投递条数。 */
        long countContaining(String fragment) {
            return items.stream().filter(item -> item.contains(fragment)).count();
        }

        /** 已记录投递条目快照（失败信息用）。 */
        List<String> items() {
            return List.copyOf(items);
        }
    }
}

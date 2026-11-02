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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * {@code audience} 扇出的**隔离性**单测（投递面直接观测，不依赖网络/Redis）。
 *
 * <p><b>为什么必须有这一层</b>：业务用 {@link SseEmitterManager#connectAudience} 把不同租户挂在
 * {@code "tenant:<id>"} 这类键上，隔离完全取决于「扇出是**精确键匹配**」这一实现性质。若哪天换成
 * 前缀匹配（{@code startsWith}）或通配，{@code tenant:1} 的推送就会命中 {@code tenant:12} ——
 * 那是**跨租户数据泄漏**，且功能测试（「推送能收到」）**全绿也发现不了**。故这里用计数替身直接数
 * 「另一条 audience 的连接被 send 了几次」，把该性质钉死。</p>
 *
 * <p>计数口径：{@link SseEmitterManager#connectAudience} 建连即发一条 {@code connected} 注释帧，
 * 故所有断言一律用「推送前基线 → 推送后」的**增量**，不用绝对值（否则改心跳/建连帧就会误红）。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
class SseAudienceIsolationTest {

    /** 计数型 emitter：只记录被 send 的次数（不写真实响应）。 */
    static final class CountingEmitter extends SseEmitter {

        final AtomicInteger sends = new AtomicInteger();

        CountingEmitter() {
            super(0L);
        }

        @Override
        public void send(SseEventBuilder builder) {
            sends.incrementAndGet();
        }
    }

    /** 真实 manager + 计数替身（{@code newEmitter} 包级可见，故本类必须在同包）。 */
    static final class ProbeManager extends SseEmitterManager {

        /** 本 manager 创建的连接（按创建顺序；用于断言「一个订阅只建了一条连接」）。 */
        final List<CountingEmitter> created = new CopyOnWriteArrayList<>();

        ProbeManager() {
            super(0L, 0);
        }

        @Override
        SseEmitter newEmitter() {
            CountingEmitter emitter = new CountingEmitter();
            created.add(emitter);
            return emitter;
        }
    }

    private ProbeManager manager;

    @BeforeEach
    void setUp() {
        manager = new ProbeManager();
    }

    @AfterEach
    void tearDown() {
        manager.destroy();
    }

    @Test
    @DisplayName("★ 前缀陷阱：推 tenant:1 时 tenant:12 的连接零增量（前缀匹配实现会串租户）")
    void tenantPushMustNotLeakToLongerKey() throws Exception {
        CountingEmitter e1 = (CountingEmitter) manager.connectAudience("tenant:1");
        CountingEmitter e12 = (CountingEmitter) manager.connectAudience("tenant:12");
        int base1 = e1.sends.get();
        int base12 = e12.sends.get();
        assertThat(base1).as("建连基线应为 1（connected 注释帧）").isEqualTo(1);

        manager.sendToAudience("tenant:1", "latest", Map.of("deviceId", 7));
        awaitIncrement(e1, base1, 3000);
        Thread.sleep(200);

        assertThat(e1.sends.get() - base1).as("目标 audience 应收到 1 次").isEqualTo(1);
        assertThat(e12.sends.get() - base12)
            .as("★ 隔离性：tenant:12 绝不能命中 tenant:1 的推送")
            .isZero();
    }

    @Test
    @DisplayName("★ 反向：推 tenant:12 时 tenant:1 零增量")
    void longerKeyPushMustNotLeakToShorterKey() throws Exception {
        CountingEmitter e1 = (CountingEmitter) manager.connectAudience("tenant:1");
        CountingEmitter e12 = (CountingEmitter) manager.connectAudience("tenant:12");
        int base1 = e1.sends.get();
        int base12 = e12.sends.get();

        manager.sendToAudience("tenant:12", "latest", Map.of("deviceId", 9));
        awaitIncrement(e12, base12, 3000);
        Thread.sleep(200);

        assertThat(e12.sends.get() - base12).isEqualTo(1);
        assertThat(e1.sends.get() - base1).isZero();
    }

    @Test
    @DisplayName("★ 保留键与租户键空间不相交：推 platform:all 对 tenant 连接零增量")
    void platformKeyMustNotReachTenantConnections() throws Exception {
        CountingEmitter e1 = (CountingEmitter) manager.connectAudience("tenant:1");
        CountingEmitter e12 = (CountingEmitter) manager.connectAudience("tenant:12");
        CountingEmitter platform = (CountingEmitter) manager.connectAudience("platform:all");
        int base1 = e1.sends.get();
        int base12 = e12.sends.get();
        int baseP = platform.sends.get();

        manager.sendToAudience("platform:all", "alert", Map.of("action", "ack"));
        awaitIncrement(platform, baseP, 3000);
        Thread.sleep(200);

        assertThat(platform.sends.get() - baseP).isEqualTo(1);
        assertThat(e1.sends.get() - base1).isZero();
        assertThat(e12.sends.get() - base12).isZero();
    }

    @Test
    @DisplayName("★ 自检（防假绿）：同 audience 推送确实产生增量 —— 否则上面三条零增量断言恒真")
    void sameAudiencePushDelivers() throws Exception {
        CountingEmitter e1 = (CountingEmitter) manager.connectAudience("tenant:1");
        int base = e1.sends.get();

        manager.sendToAudience("tenant:1", "latest", Map.of("deviceId", 7));
        awaitIncrement(e1, base, 3000);

        assertThat(e1.sends.get() - base)
            .as("自检失败 ⇒ 上面几条「零增量」断言是假绿（推送根本没发生）")
            .isEqualTo(1);
    }

    @Test
    @DisplayName("★ broadcast 命中所有 audience —— 钉住「不得用 broadcast 发单租户/平台事件」的理由")
    void broadcastHitsEveryAudience() throws Exception {
        CountingEmitter e1 = (CountingEmitter) manager.connectAudience("tenant:1");
        CountingEmitter e12 = (CountingEmitter) manager.connectAudience("tenant:12");
        int base1 = e1.sends.get();
        int base12 = e12.sends.get();

        manager.broadcast("latest", Map.of("deviceId", 7));
        awaitIncrement(e1, base1, 3000);
        awaitIncrement(e12, base12, 3000);

        assertThat(e1.sends.get() - base1).isEqualTo(1);
        assertThat(e12.sends.get() - base12)
            .as("broadcast 会命中所有租户连接 ⇒ 业务不得用它发单租户事件，也不得用它发「平台专属」事件")
            .isEqualTo(1);
    }

    @Test
    @DisplayName("回归：connect(userId) 仍是 connectAudience(userId) 的特例")
    void connectStillDelegatesToAudience() {
        manager.connect("u1");
        assertThat(manager.isOnline("u1")).isTrue();
        assertThat(manager.onlineAudiences()).containsExactly("u1");
    }

    @Test
    @DisplayName("★ connectAll 只建一条连接，并注册到每个维度（多建的 emitter 永远挂不到响应上）")
    void connectAllUsesSingleConnectionForEveryAudience() {
        CountingEmitter returned = (CountingEmitter) manager.connectAll(Set.of("u1", "tenant:7"));

        assertThat(manager.created)
            .as("HTTP 响应只能带回一个 emitter；为每个维度各建一条会产生永远发不出帧、也永不回收的连接")
            .hasSize(1)
            .containsExactly(returned);
        assertThat(manager.isOnline("u1")).isTrue();
        assertThat(manager.isOnline("tenant:7")).isTrue();
        assertThat(manager.onlineAudiences()).containsExactlyInAnyOrder("u1", "tenant:7");
    }

    @Test
    @DisplayName("★ 多维度订阅：响应里的那条连接能收到每个维度的事件（修前只有第一条维度能收到）")
    void singleConnectionReceivesEveryAudienceEvent() throws Exception {
        CountingEmitter emitter = (CountingEmitter) manager.connectAll(Set.of("u1", "tenant:7"));
        int base = emitter.sends.get();

        manager.sendToAudience("u1", "latest", Map.of("k", 1));
        awaitIncrement(emitter, base, 3000);
        manager.sendToAudience("tenant:7", "latest", Map.of("k", 2));
        awaitIncrement(emitter, base + 1, 3000);

        assertThat(emitter.sends.get() - base)
            .as("两个维度的事件都必须落到同一条（真正交给响应的）连接上")
            .isEqualTo(2);
    }

    @Test
    @DisplayName("★ 广播按连接身份去重：一条连接挂多个维度只收一次")
    void broadcastDeduplicatesMultiAudienceConnection() throws Exception {
        CountingEmitter emitter = (CountingEmitter) manager.connectAll(Set.of("u1", "tenant:7"));
        int base = emitter.sends.get();

        manager.broadcast("latest", Map.of("k", 1));
        awaitIncrement(emitter, base, 3000);
        Thread.sleep(200);

        assertThat(emitter.sends.get() - base)
            .as("同一条连接注册在两个维度下，广播若按注册键逐个发会让客户端收到重复事件")
            .isEqualTo(1);
    }

    /** 等增量出现（投递在虚拟线程里异步执行），超时即失败。 */
    private static void awaitIncrement(CountingEmitter emitter, int baseline, long timeoutMs)
        throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (emitter.sends.get() <= baseline && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
    }
}

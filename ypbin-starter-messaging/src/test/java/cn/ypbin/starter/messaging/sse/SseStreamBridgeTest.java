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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Hooks;
import reactor.core.scheduler.Schedulers;

/**
 * {@link SseStreamBridge} 运行时用例。
 *
 * <p>锁定「错了也看不出」的语义：逐 token 顺序、错误帧先于 complete、
 * <strong>超时/断开/发送失败必须释放上游订阅</strong>（最容易漏的泄漏点）、空流、错误文案定制、
 * SSE 报文逐字节兼容，以及上下文跨线程传播。使用记录型 emitter 替身直接采集
 * {@link SseEmitter.SseEventBuilder#build()} 的<strong>原始片段</strong>，因此断言的是真实报文而非近似描述。</p>
 *
 * <p>上下文传播用例真实依赖 micrometer {@code context-propagation}（messaging 的 optional 依赖，
 * 但测试 classpath 上存在）；缺依赖时的退化路径由
 * {@link SseStreamBridgeNoContextPropagationTest} 用过滤类加载器单独覆盖。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
class SseStreamBridgeTest {

    /** 默认等待时长：流式回调在其它线程执行，留足余量避免慢机 flaky。 */
    private static final long AWAIT_MILLIS = 5_000L;

    @Test
    @DisplayName("正常多 token：按顺序逐帧发出，不缓冲，完成后 complete")
    void streamsTokensInOrderAndCompletes() throws Exception {
        RecordingEmitter emitter = new RecordingEmitter();
        List<String> tokens = List.of("你", "好", "，", "世界");

        SseEmitter returned = bridgeFor(emitter).stream(Flux.fromIterable(tokens),
            SseStreamOptions.defaults());

        assertThat(returned).isSameAs(emitter);
        assertThat(emitter.awaitCompletion()).as("流应在超时前完成").isTrue();
        assertThat(emitter.tokenData()).as("每个 token 必须立即且按序发出，不得聚合")
            .containsExactlyElementsOf(tokens);
        assertThat(emitter.completed.get()).as("正常结束必须 complete").isTrue();
    }

    @Test
    @DisplayName("★ SSE 报文逐字节兼容：默认发裸 data: 帧，与既有 emitter.send(token) 完全一致")
    void defaultFrameIsByteCompatibleWithLegacySend() throws Exception {
        RecordingEmitter emitter = new RecordingEmitter();
        bridgeFor(emitter).stream(Flux.just("tok"), SseStreamOptions.defaults());
        assertThat(emitter.awaitCompletion()).isTrue();

        // 既有实现是 emitter.send(token)，Spring 内部即 event().data(token)：
        // 帧片段必须逐字节相等，否则前端解析器行为改变（真实线上协议）
        assertThat(emitter.fragments()).containsExactly("data:", "tok", "\n\n");
    }

    @Test
    @DisplayName("★ 错误路径：error 帧先于 complete（帧内容 event:error\\ndata: + 文案）")
    void onErrorSendsErrorFrameThenCompletes() throws Exception {
        RecordingEmitter emitter = new RecordingEmitter();
        IllegalStateException root = new IllegalStateException("模型连接被重置");
        RuntimeException wrapper = new RuntimeException("调用大模型失败", root);

        bridgeFor(emitter).stream(
            Flux.concat(Flux.just("部分"), Flux.<String>error(wrapper)),
            SseStreamOptions.defaults());

        assertThat(emitter.awaitCompletion()).as("出错后也必须结束响应，不能挂死").isTrue();
        assertThat(emitter.fragments()).as("错误帧必须是 event:error\\ndata:根因，且出现在 token 之后")
            .containsSubsequence("data:", "部分", "\n\n", "event:error\ndata:", "对话出错：模型连接被重置", "\n\n");
        assertThat(emitter.completed.get()).isTrue();
    }

    @Test
    @DisplayName("★ 错误路径：上游订阅被释放（源侧 onDispose 触发，且不再有回调）")
    void onErrorDisposesUpstreamSubscription() throws Exception {
        RecordingEmitter emitter = new RecordingEmitter();
        AtomicBoolean upstreamDisposed = new AtomicBoolean(false);
        FluxSink<String>[] sinkRef = newSinkRef();
        Flux<String> upstream = Flux.<String>create(sink -> {
            sinkRef[0] = sink;
            // 实测：错误终止走 sink.onDispose（doOnCancel 只在取消时触发）
            sink.onDispose(() -> upstreamDisposed.set(true));
        });

        bridgeFor(emitter).stream(upstream, SseStreamOptions.defaults());
        sinkRef[0].error(new IllegalStateException("boom"));

        assertThat(emitter.awaitCompletion()).isTrue();
        assertThat(upstreamDisposed.get()).as("错误路径必须释放上游订阅").isTrue();
        assertThat(emitter.fragments())
            .contains("event:error\ndata:", "对话出错：boom", "\n\n");
    }

    @Test
    @DisplayName("★ 客户端断开（onError 回调）：必须释放上游订阅，否则 Reactor 流泄漏")
    void clientErrorDisposesUpstreamSubscription() throws Exception {
        RecordingEmitter emitter = new RecordingEmitter();
        CountDownLatch cancelled = new CountDownLatch(1);
        AtomicInteger emitted = new AtomicInteger();
        FluxSink<String>[] sinkRef = newSinkRef();

        SseEmitter result = bridgeFor(emitter)
            .stream(fluxThroughSink(sinkRef, emitted, cancelled), SseStreamOptions.defaults());
        assertThat(result).isSameAs(emitter);

        sinkRef[0].next("first");
        Thread.sleep(150);
        assertThat(emitted.get()).as("前置条件：上游确实推送了 token").isPositive();
        assertThat(emitter.tokenData()).containsExactly("first");

        // 客户端断开：Servlet 容器触发 onError 回调（真实场景即 IOException / 连接复位）
        emitter.fireError(new IllegalStateException("client aborted"));
        assertThat(cancelled.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS))
            .as("★ onError 未释放上游订阅：连接已死但 Reactor 流仍在生产 token（资源泄漏）")
            .isTrue();

        int countAfterDispose = emitter.tokenData().size();
        sinkRef[0].next("after-dispose");
        Thread.sleep(150);
        assertThat(emitter.tokenData()).as("订阅释放后上游 token 不得继续写入已断开的连接")
            .hasSize(countAfterDispose);
    }

    @Test
    @DisplayName("★ 容器超时（onTimeout 回调）：必须释放上游订阅")
    void clientTimeoutDisposesUpstreamSubscription() throws Exception {
        RecordingEmitter emitter = new RecordingEmitter();
        CountDownLatch cancelled = new CountDownLatch(1);
        AtomicInteger emitted = new AtomicInteger();
        FluxSink<String>[] sinkRef = newSinkRef();

        bridgeFor(emitter).stream(fluxThroughSink(sinkRef, emitted, cancelled), SseStreamOptions.defaults());
        emitter.fireTimeout();

        assertThat(cancelled.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS))
            .as("★ 超时未释放上游订阅：长连接超时后 Reactor 流继续空转")
            .isTrue();
    }

    @Test
    @DisplayName("★ 完成回调（onCompletion）：释放上游订阅（覆盖容器不触发 onError 的断开路径）")
    void completionDisposesUpstreamSubscription() throws Exception {
        RecordingEmitter emitter = new RecordingEmitter();
        CountDownLatch cancelled = new CountDownLatch(1);
        AtomicInteger emitted = new AtomicInteger();
        FluxSink<String>[] sinkRef = newSinkRef();

        bridgeFor(emitter).stream(fluxThroughSink(sinkRef, emitted, cancelled), SseStreamOptions.defaults());
        sinkRef[0].next("x");
        Thread.sleep(100);
        emitter.fireCompletion();

        assertThat(cancelled.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS))
            .as("★ onCompletion 未释放上游订阅：客户端断开后容器走完成路径，流照样泄漏")
            .isTrue();
    }

    @Test
    @DisplayName("空流：不发任何 token，直接 complete")
    void emptyFluxCompletesWithoutTokens() throws Exception {
        RecordingEmitter emitter = new RecordingEmitter();
        bridgeFor(emitter).stream(Flux.<String>empty(), SseStreamOptions.defaults());

        assertThat(emitter.awaitCompletion()).isTrue();
        assertThat(emitter.tokenData()).isEmpty();
        assertThat(emitter.fragments()).as("空流不得发出任何帧").isEmpty();
        assertThat(emitter.completed.get()).isTrue();
    }

    @Test
    @DisplayName("发送失败（客户端已断）：释放上游订阅并 complete")
    void sendFailureDisposesAndCompletes() throws Exception {
        RecordingEmitter emitter = new RecordingEmitter();
        emitter.failOnSend = true;
        CountDownLatch cancelled = new CountDownLatch(1);
        AtomicInteger emitted = new AtomicInteger();
        FluxSink<String>[] sinkRef = newSinkRef();

        bridgeFor(emitter).stream(fluxThroughSink(sinkRef, emitted, cancelled), SseStreamOptions.defaults());
        sinkRef[0].next("boom");

        assertThat(cancelled.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS))
            .as("发送失败说明连接已断，必须释放上游订阅").isTrue();
        assertThat(emitter.completed.get()).as("发送失败后应结束响应，避免调用方继续等待").isTrue();
    }

    @Test
    @DisplayName("错误文案可定制：前缀与错误事件名按选项生效")
    void errorMessageAndEventNameAreCustomizable() throws Exception {
        RecordingEmitter emitter = new RecordingEmitter();
        SseStreamOptions options = SseStreamOptions.builder()
            .errorPrefix("生成失败：")
            .errorEventName("stream-error")
            .build();

        bridgeFor(emitter).stream(Flux.<String>error(new IllegalArgumentException("限流")), options);

        assertThat(emitter.awaitCompletion()).isTrue();
        assertThat(emitter.fragments())
            .containsSubsequence("event:stream-error\ndata:", "生成失败：限流", "\n\n");
    }

    @Test
    @DisplayName("关闭错误帧开关：出错只 complete，不发错误事件")
    void errorEventCanBeDisabled() throws Exception {
        RecordingEmitter emitter = new RecordingEmitter();
        SseStreamOptions options = SseStreamOptions.builder().errorEventEnabled(false).build();

        bridgeFor(emitter).stream(Flux.<String>error(new IllegalStateException("x")), options);

        assertThat(emitter.awaitCompletion()).isTrue();
        assertThat(emitter.fragments()).as("关闭后不得发出任何错误帧").isEmpty();
        assertThat(emitter.completed.get()).isTrue();
    }

    @Test
    @DisplayName("★ 错误信号被订阅者消费：不触发 Reactor onErrorDropped 全局兜底（错误不再无人消费）")
    void streamErrorIsConsumedAndNeverDropped() throws Exception {
        RecordingEmitter emitter = new RecordingEmitter();
        List<Throwable> dropped = new CopyOnWriteArrayList<>();
        // 复现原缺陷：doOnError 只是副作用算子，无 errorConsumer 时错误会冒泡到全局 onErrorDropped
        Hooks.onErrorDropped(dropped::add);
        try {
            bridgeFor(emitter).stream(Flux.<String>error(new IllegalStateException("上游故障")),
                SseStreamOptions.defaults());

            assertThat(emitter.awaitCompletion()).isTrue();
            assertThat(dropped).as("★ 错误信号无人消费：Reactor 走了 onErrorDropped 全局兜底").isEmpty();
            assertThat(emitter.fragments()).as("错误帧仍按契约发出").contains("event:error\ndata:", "对话出错：上游故障");
        } finally {
            Hooks.resetOnErrorDropped();
        }
    }

    @Test
    @DisplayName("★ 客户端断开（容器类名命中）：不补发错误帧，日志降级 DEBUG 且不带完整堆栈")
    void clientDisconnectSkipsErrorFrameAndDowngradesLog() throws Exception {
        RecordingEmitter emitter = new RecordingEmitter();
        try (LogCapture logs = new LogCapture()) {
            // 客户端中止由容器以 ClientAbortException 抛出（按类名命中）；上游判定不看消息文案
            bridgeFor(emitter).stream(Flux.<String>error(new ClientAbortException()),
                SseStreamOptions.defaults());

            assertThat(emitter.awaitCompletion()).isTrue();
            assertThat(emitter.fragments()).as("★ 连接已断：不得向死连接补发错误帧").isEmpty();
            assertThat(emitter.completed.get()).as("仍须结束响应，不能挂死").isTrue();
            assertThat(logs.events(Level.ERROR)).as("客户端断开不得刷 ERROR").isEmpty();
            assertThat(logs.events(Level.WARN)).as("客户端断开不得刷 WARN").isEmpty();
            assertThat(logs.events(Level.DEBUG)).as("应降级为低噪声日志").isNotEmpty();
            assertThat(logs.events()).allSatisfy(event ->
                assertThat(event.getThrowableProxy()).as("降级日志不得携带完整堆栈").isNull());
        }
    }

    @Test
    @DisplayName("★ 上游 IOException(\"Connection reset\")：属服务端真实故障，必须 ERROR + 完整堆栈 + 错误帧")
    void upstreamConnectionResetIsNotSilencedAsClientDisconnect() throws Exception {
        RecordingEmitter emitter = new RecordingEmitter();
        try (LogCapture logs = new LogCapture()) {
            // 上游服务端（模型 API / 下游 HTTP）重置连接的典型形态；不得按消息启发式降级
            bridgeFor(emitter).stream(Flux.<String>error(new IOException("Connection reset")),
                SseStreamOptions.defaults());

            assertThat(emitter.awaitCompletion()).isTrue();
            assertThat(emitter.fragments())
                .as("★ 上游重置连接不得被当成客户端断开：必须补发错误帧，否则客户端看到被截断却「正常结束」的流")
                .containsSubsequence("event:error\ndata:", "对话出错：Connection reset", "\n\n");
            assertThat(logs.events(Level.ERROR)).as("上游真实故障必须有 ERROR 记录").isNotEmpty();
            assertThat(logs.events(Level.ERROR)).anySatisfy(event ->
                assertThat(event.getThrowableProxy()).as("★ 必须保留完整堆栈，不得只留 message").isNotNull());
            assertThat(logs.events(Level.DEBUG)).as("★ 上游真实故障不得降级为 DEBUG").isEmpty();
        }
    }

    @Test
    @DisplayName("★ 客户端断开（容器异步异常）：同样跳过错误帧并降级")
    void asyncRequestNotUsableSkipsErrorFrame() throws Exception {
        RecordingEmitter emitter = new RecordingEmitter();
        try (LogCapture logs = new LogCapture()) {
            bridgeFor(emitter).stream(
                Flux.<String>error(new AsyncRequestNotUsableException("Servlet 异步请求不再可用")),
                SseStreamOptions.defaults());

            assertThat(emitter.awaitCompletion()).isTrue();
            assertThat(emitter.fragments()).as("★ 连接已不可用：不得补发错误帧").isEmpty();
            assertThat(logs.events(Level.ERROR)).isEmpty();
            assertThat(logs.events(Level.WARN)).isEmpty();
            assertThat(logs.events(Level.DEBUG)).isNotEmpty();
        }
    }

    @Test
    @DisplayName("★ 服务端真实故障：仍发错误帧，并保留 ERROR + 完整堆栈（可观测性不降级）")
    void serverFaultKeepsErrorFrameAndFullStack() throws Exception {
        RecordingEmitter emitter = new RecordingEmitter();
        try (LogCapture logs = new LogCapture()) {
            bridgeFor(emitter).stream(Flux.<String>error(new IllegalStateException("模型服务不可用")),
                SseStreamOptions.defaults());

            assertThat(emitter.awaitCompletion()).isTrue();
            assertThat(emitter.fragments()).as("服务端故障仍须发错误帧（对外契约不变）")
                .containsSubsequence("event:error\ndata:", "对话出错：模型服务不可用", "\n\n");
            assertThat(logs.events(Level.ERROR)).as("服务端故障必须有 ERROR 记录").isNotEmpty();
            assertThat(logs.events(Level.ERROR)).anySatisfy(event ->
                assertThat(event.getThrowableProxy()).as("★ 必须保留完整堆栈，不得只留 message").isNotNull());
        }
    }

    @Test
    @DisplayName("★ 发送路径断开（Broken pipe）：日志降级 DEBUG 且不带堆栈，仍释放上游订阅，且不发错误帧")
    void sendFailureOnDisconnectIsDowngraded() throws Exception {
        RecordingEmitter emitter = new RecordingEmitter();
        emitter.failOnSend = true;
        emitter.sendFailure = new IOException("Broken pipe");
        try (LogCapture logs = new LogCapture()) {
            CountDownLatch cancelled = new CountDownLatch(1);
            AtomicInteger emitted = new AtomicInteger();
            FluxSink<String>[] sinkRef = newSinkRef();

            bridgeFor(emitter).stream(fluxThroughSink(sinkRef, emitted, cancelled), SseStreamOptions.defaults());
            sinkRef[0].next("boom");

            assertThat(cancelled.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS))
                .as("发送失败说明连接已断，必须释放上游订阅").isTrue();
            assertThat(emitter.fragments()).as("★ 客户端已断：不得补发错误帧").isEmpty();
            assertThat(logs.events(Level.ERROR)).isEmpty();
            assertThat(logs.events(Level.WARN)).as("断开类发送失败不得刷 WARN").isEmpty();
            assertThat(logs.events(Level.DEBUG)).isNotEmpty();
            assertThat(logs.events()).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
        }
    }

    @Test
    @DisplayName("★ 错误帧补发失败：断开类降级 DEBUG；其余异常仍 WARN + 完整堆栈")
    void errorFrameSendFailureIsClassified() throws Exception {
        RecordingEmitter disconnected = new RecordingEmitter();
        disconnected.failOnSend = true;
        disconnected.sendFailure = new AsyncRequestNotUsableException("连接已不可用");
        try (LogCapture logs = new LogCapture()) {
            SseStreamBridge.sendErrorFrame(disconnected, "文案", SseStreamOptions.defaults());
            assertThat(logs.events(Level.WARN)).as("连接已断不得刷 WARN").isEmpty();
            assertThat(logs.events(Level.DEBUG)).isNotEmpty();
        }

        RecordingEmitter broken = new RecordingEmitter();
        broken.failOnSend = true;
        broken.sendFailure = new IOException("序列化失败");
        try (LogCapture logs = new LogCapture()) {
            SseStreamBridge.sendErrorFrame(broken, "文案", SseStreamOptions.defaults());
            assertThat(logs.events(Level.WARN)).as("发送链路自身异常不得静默").isNotEmpty();
            assertThat(logs.events(Level.WARN)).anySatisfy(event ->
                assertThat(event.getThrowableProxy()).isNotNull());
        }
    }

    @Test
    @DisplayName("★ 上游断开判定只认类型/类名：IOException 的消息形态一律不算（防服务端重置被静默）")
    void upstreamDisconnectDetectionIsTypeOnly() {
        assertThat(SseStreamBridge.isClientDisconnectFromUpstream(
            new AsyncRequestNotUsableException("异步请求已不可用"))).isTrue();
        assertThat(SseStreamBridge.isClientDisconnectFromUpstream(new ClientAbortException()))
            .as("Tomcat 容器中止按类名命中").isTrue();
        assertThat(SseStreamBridge.isClientDisconnectFromUpstream(new RuntimeException("包装两层",
            new IllegalStateException("中层", new AsyncRequestNotUsableException("异步请求已不可用")))))
            .as("cause 链逐层判定").isTrue();

        // 关键差异：上游 IOException 的消息形态不得判为客户端断开——上游服务端同样会发这些消息
        assertThat(SseStreamBridge.isClientDisconnectFromUpstream(new IOException("Broken pipe")))
            .as("★ 上游 Broken pipe 也可能是服务端侧断开，不得按消息降级").isFalse();
        assertThat(SseStreamBridge.isClientDisconnectFromUpstream(new IOException("Connection reset by peer")))
            .as("★ 上游 Connection reset 是模型 API/下游 HTTP 重置连接的典型形态，必须走 ERROR").isFalse();
        assertThat(SseStreamBridge.isClientDisconnectFromUpstream(new IllegalStateException("模型服务不可用")))
            .isFalse();
        assertThatThrownBy(() -> SseStreamBridge.isClientDisconnectFromUpstream(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("★ 发送路径断开判定：类型/类名 + IOException 消息启发式（含 cause 链）")
    void writePathDisconnectDetectionUsesMessageHeuristic() {
        assertThat(SseStreamBridge.isClientDisconnectOnWrite(new AsyncRequestNotUsableException("异步请求已不可用")))
            .isTrue();
        assertThat(SseStreamBridge.isClientDisconnectOnWrite(new IOException("Broken pipe"))).isTrue();
        assertThat(SseStreamBridge.isClientDisconnectOnWrite(new IOException("Connection reset by peer"))).isTrue();
        assertThat(SseStreamBridge.isClientDisconnectOnWrite(new RuntimeException("包装两层",
            new IllegalStateException("中层", new IOException("Connection reset")))))
            .as("cause 链逐层判定").isTrue();
        // Tomcat 的 ClientAbortException 不在 messaging 编译期 classpath：按类名命中。
        // 该类型由测试替身提供（org.apache.catalina.connector.ClientAbortException，仅同名，非真实容器类）
        assertThat(SseStreamBridge.isClientDisconnectOnWrite(new ClientAbortException())).isTrue();

        assertThat(SseStreamBridge.isClientDisconnectOnWrite(new IllegalStateException("模型服务不可用")))
            .as("服务端真实故障不得误判为断开").isFalse();
        assertThat(SseStreamBridge.isClientDisconnectOnWrite(new IOException("connect timed out")))
            .as("连接超时属服务端/网络故障，不得降级为客户端断开").isFalse();
        assertThat(SseStreamBridge.isClientDisconnectOnWrite(new NullPointerException())).isFalse();
        assertThatThrownBy(() -> SseStreamBridge.isClientDisconnectOnWrite(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("显式配置事件名：写出 event:message 行")
    void explicitEventNameWritesEventLine() throws Exception {
        RecordingEmitter emitter = new RecordingEmitter();
        SseStreamOptions options = SseStreamOptions.builder().eventName("message").build();

        bridgeFor(emitter).stream(Flux.just("tok"), options);

        assertThat(emitter.awaitCompletion()).isTrue();
        assertThat(emitter.fragments()).containsExactly("event:message\ndata:", "tok", "\n\n");
    }

    @Test
    @DisplayName("errorEmitter：错误帧内容为 event:error\\ndata:文案 且立即完成")
    void errorEmitterSendsErrorFrameAndCompletes() throws Exception {
        // 与 errorEmitter 共用同一发送路径（sendErrorFrame），故这里断言的就是生产会写出的字节
        RecordingEmitter emitter = new RecordingEmitter();
        SseStreamBridge.sendErrorFrame(emitter, "AI 模块未启用", SseStreamOptions.defaults());
        assertThat(emitter.fragments())
            .containsExactly("event:error\ndata:", "AI 模块未启用", "\n\n");

        SseEmitter real = SseStreamBridge.errorEmitter("AI 模块未启用");
        assertThat(real).isNotNull();
        assertThat(real.getTimeout()).as("默认不设总超时").isEqualTo(0L);

        SseEmitter custom = SseStreamBridge.errorEmitter("boom",
            SseStreamOptions.builder().errorEventName("stream-error").build());
        assertThat(custom).isNotNull();
    }

    @Test
    @DisplayName("选项不变量：默认选项为「不超时 + 裸 data: 帧 + error 事件名 + 开传播」")
    void defaultOptionsAreStable() {
        SseStreamOptions defaults = SseStreamOptions.defaults();
        assertThat(defaults.timeoutMillis()).isZero();
        assertThat(defaults.eventName()).isEmpty();
        assertThat(defaults.errorEventName()).isEqualTo("error");
        assertThat(defaults.errorPrefix()).isEqualTo("对话出错：");
        assertThat(defaults.propagateContext()).isTrue();
        assertThat(defaults.errorEventEnabled()).isTrue();
        // 显式关闭传播时应退化为「不写 Context、不还原线程」，但仍正常发帧
        assertThat(SseStreamOptions.builder().propagateContext(false).build().propagateContext()).isFalse();
        assertThat(SseStreamOptions.builder().timeout(java.time.Duration.ofSeconds(30)).build().timeoutMillis())
            .isEqualTo(30_000L);
    }

    @Test
    @DisplayName("rootMessage：取最深层原因；无消息时退化为类简单名")
    void rootMessageWalksToDeepestCause() {
        assertThat(SseStreamBridge.rootMessage(
            new RuntimeException("外层", new IllegalStateException("最深层"))))
            .isEqualTo("最深层");
        assertThat(SseStreamBridge.rootMessage(new IllegalStateException())).isEqualTo("IllegalStateException");
        assertThat(SseStreamBridge.rootMessage(new RuntimeException("", new IllegalStateException("  "))))
            .isEqualTo("IllegalStateException");
    }

    @Test
    @DisplayName("★ 上下文传播：桥接回调（发帧处）能看到请求线程的租户上下文（不依赖 Hooks 开关）")
    void bridgeCallbacksSeePropagatedThreadLocalContext() throws Exception {
        String key = "ypbin.test.probe." + System.nanoTime();
        ThreadLocal<String> probe = new ThreadLocal<>();
        registerProbeAccessor(key, probe);
        try {
            RecordingEmitter emitter = new RecordingEmitter();
            // 每次 send（即桥接的 doOnNext 回调）读取探针：该回调由快照作用域包裹
            emitter.contextProbe = probe;
            probe.set("tenant-42");

            // 上游切到别的线程生产 token，模拟模型回调线程
            bridgeFor(emitter).stream(Flux.just("a", "b").publishOn(Schedulers.boundedElastic()),
                SseStreamOptions.defaults());
            probe.remove();

            assertThat(emitter.awaitCompletion()).isTrue();
            assertThat(emitter.propagatedValuesAtSend)
                .as("★ 发帧回调读不到租户上下文：多租户下流式输出会丢失/串租户上下文")
                .containsExactly("tenant-42", "tenant-42");
        } finally {
            ContextRegistry.getInstance().removeThreadLocalAccessor(key);
            probe.remove();
        }
    }

    @Test
    @DisplayName("★ 上下文传播：开启 Reactor 自动传播后，调用方自己的上游算子也能看到上下文")
    void upstreamOperatorsSeeContextWhenAutomaticPropagationEnabled() throws Exception {
        String key = "ypbin.test.upstream." + System.nanoTime();
        ThreadLocal<String> probe = new ThreadLocal<>();
        registerProbeAccessor(key, probe);
        boolean hooksWereEnabled = reactor.core.publisher.Hooks.isAutomaticContextPropagationEnabled();
        // 生产环境由 ypbin-starter-ai 的 AiChatAutoConfiguration 开启（幂等）
        reactor.core.publisher.Hooks.enableAutomaticContextPropagation();
        try {
            RecordingEmitter emitter = new RecordingEmitter();
            AtomicInteger sawValue = new AtomicInteger();
            AtomicInteger callbacks = new AtomicInteger();

            probe.set("tenant-42");
            Flux<String> upstream = Flux.just("a", "b")
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(token -> {
                    callbacks.incrementAndGet();
                    if ("tenant-42".equals(probe.get())) {
                        sawValue.incrementAndGet();
                    }
                });
            bridgeFor(emitter).stream(upstream, SseStreamOptions.defaults());
            probe.remove();

            assertThat(emitter.awaitCompletion()).isTrue();
            assertThat(callbacks.get()).as("前置条件：上游两个 token 都应在其它线程产出").isEqualTo(2);
            assertThat(sawValue.get())
                .as("★ 上游算子读不到上下文：桥接注入的 Context 未生效（业务落库/统计会拿不到租户）")
                .isEqualTo(2);
        } finally {
            if (!hooksWereEnabled) {
                reactor.core.publisher.Hooks.disableAutomaticContextPropagation();
            }
            ContextRegistry.getInstance().removeThreadLocalAccessor(key);
            probe.remove();
        }
    }

    @Test
    @DisplayName("上下文传播不污染调用线程：快照作用域关闭后恢复原值")
    void propagationRestoresCallerThreadContext() throws Exception {
        String key = "ypbin.test.restore." + System.nanoTime();
        ThreadLocal<String> probe = new ThreadLocal<>();
        registerProbeAccessor(key, probe);
        try {
            RecordingEmitter emitter = new RecordingEmitter();
            probe.set("caller-original");
            bridgeFor(emitter).stream(Flux.just("t"), SseStreamOptions.defaults());
            assertThat(emitter.awaitCompletion()).isTrue();
            assertThat(probe.get())
                .as("桥接不得把调用线程自身的上下文改掉（备份-恢复语义）")
                .isEqualTo("caller-original");
        } finally {
            ContextRegistry.getInstance().removeThreadLocalAccessor(key);
            probe.remove();
        }
    }

    @Test
    @DisplayName("参数与选项校验：null 上游 / 空事件名必须显式报错，不得静默")
    void rejectsInvalidArguments() {
        SseStreamBridge bridge = new SseStreamBridge();
        assertThatThrownBy(() -> bridge.stream(null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> bridge.stream(Flux.empty(), null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SseStreamOptions.builder().errorEventName(" ").build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("errorEventName");
        assertThatThrownBy(() -> SseStreamBridge.errorEmitter(null))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SseStreamBridge.rootMessage(null))
            .isInstanceOf(NullPointerException.class);
    }

    // ========== 测试辅助 ==========

    /**
     * 注入记录型 emitter 的桥接实例：覆写包级可见的 {@link SseStreamBridge#createEmitter} 扩展点
     * （与 {@code SseEmitterManager#newEmitter()} 同一测试约定），生产逻辑本身完全走真实实现。
     */
    private static SseStreamBridge bridgeFor(RecordingEmitter emitter) {
        return new SseStreamBridge() {
            @Override
            SseEmitter createEmitter(SseStreamOptions options) {
                return emitter;
            }
        };
    }

    /**
     * 注入记录型 emitter 且固定传播实现的桥接实例。
     */
    static SseStreamBridge bridgeFor(RecordingEmitter emitter, SseContextPropagator propagator) {
        return new SseStreamBridge(propagator) {
            @Override
            SseEmitter createEmitter(SseStreamOptions options) {
                return emitter;
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static FluxSink<String>[] newSinkRef() {
        return new FluxSink[1];
    }

    /** 可手动推送、可观察取消的上游流（模拟仍在生产 token 的长连模型回调）。 */
    private static Flux<String> fluxThroughSink(FluxSink<String>[] sinkRef, AtomicInteger emitted,
                                                CountDownLatch cancelled) {
        return Flux.<String>create(sink -> sinkRef[0] = sink)
            .doOnNext(token -> emitted.incrementAndGet())
            .doOnCancel(cancelled::countDown);
    }

    /**
     * 记录型 {@link SseEmitter} 替身：采集 {@code SseEventBuilder.build()} 的<strong>原始片段</strong>
     * （即真实写入响应的字节），并可手动触发超时/错误/完成回调。
     *
     * <p>{@link SseEmitter} 的回调在真实容器里由 Servlet 异步机制触发。测试里没有容器，故覆写注册方法
     * 把回调留存下来，由用例显式 {@code fireTimeout()/fireError()/fireCompletion()}——这样能精确复现
     * 「客户端断开/超时」这条最容易漏的释放路径。</p>
     */
    static class RecordingEmitter extends SseEmitter {

        private final List<String> fragments = new CopyOnWriteArrayList<>();

        final AtomicBoolean completed = new AtomicBoolean(false);

        private final List<Runnable> timeoutCallbacks = new CopyOnWriteArrayList<>();

        private final List<Consumer<Throwable>> errorCallbacks = new CopyOnWriteArrayList<>();

        private final List<Runnable> completionCallbacks = new CopyOnWriteArrayList<>();

        private final CountDownLatch completionLatch = new CountDownLatch(1);

        /** 是否让后续 send 抛错（复现「客户端已断开」）。 */
        volatile boolean failOnSend;

        /** 发送失败时抛出的异常（默认 {@code IOException("client aborted")}；可换成真实断开形态）。 */
        volatile IOException sendFailure = new IOException("client aborted");

        /** 可选探针：非空时记录每次发帧时当前线程读到的值（验证上下文传播）。 */
        volatile ThreadLocal<String> contextProbe;

        final List<String> propagatedValuesAtSend = new CopyOnWriteArrayList<>();

        @Override
        public void onTimeout(Runnable callback) {
            timeoutCallbacks.add(callback);
        }

        @Override
        public void onError(Consumer<Throwable> callback) {
            errorCallbacks.add(callback);
        }

        @Override
        public void onCompletion(Runnable callback) {
            completionCallbacks.add(callback);
        }

        @Override
        public void complete() {
            completed.set(true);
            completionLatch.countDown();
        }

        void fireTimeout() {
            timeoutCallbacks.forEach(Runnable::run);
        }

        void fireError(Throwable error) {
            errorCallbacks.forEach(callback -> callback.accept(error));
        }

        void fireCompletion() {
            completionCallbacks.forEach(Runnable::run);
        }

        boolean awaitCompletion() throws InterruptedException {
            return completionLatch.await(AWAIT_MILLIS, TimeUnit.MILLISECONDS);
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (failOnSend) {
                throw sendFailure;
            }
            ThreadLocal<String> probe = contextProbe;
            if (probe != null) {
                propagatedValuesAtSend.add(probe.get());
            }
            for (ResponseBodyEmitter.DataWithMediaType item : builder.build()) {
                fragments.add(String.valueOf(item.getData()));
            }
        }

        @Override
        public void send(Object object) throws IOException {
            if (failOnSend) {
                throw sendFailure;
            }
            // 生产路径走 send(builder)；此重载仅为完整性保留（若被调用则等价于 data: 帧）
            fragments.add("data:");
            fragments.add(String.valueOf(object));
            fragments.add("\n\n");
        }

        /** 采集到的全部原始帧片段（按顺序；真实写入响应的字节）。 */
        List<String> fragments() {
            return new ArrayList<>(fragments);
        }

        /** 仅正常 token 的数据（跳过错误帧的头部与文案）。 */
        List<String> tokenData() {
            List<String> result = new ArrayList<>();
            boolean inError = false;
            for (int i = 0; i < fragments.size(); i++) {
                String text = fragments.get(i);
                if (text.startsWith("event:")) {
                    inError = true;
                    continue;
                }
                if ("\n\n".equals(text)) {
                    inError = false;
                    continue;
                }
                if (inError) {
                    continue;
                }
                if ("data:".equals(text)) {
                    result.add(fragments.get(i + 1));
                }
            }
            return result;
        }
    }

    /** 注册临时探针 ThreadLocalAccessor，验证通用快照会带上「已注册」的上下文。 */
    static void registerProbeAccessor(String key, ThreadLocal<String> threadLocal) {
        ContextRegistry.getInstance().registerThreadLocalAccessor(new ThreadLocalAccessor<String>() {
            @Override
            public Object key() {
                return key;
            }

            @Override
            public String getValue() {
                return threadLocal.get();
            }

            @Override
            public void setValue(String value) {
                threadLocal.set(value);
            }

            @Override
            public void setValue() {
                threadLocal.remove();
            }
        });
    }

    /**
     * 桥接日志捕获：把 {@link SseStreamBridge} 的 logger 临时调到 DEBUG 并挂 {@link ListAppender}。
     *
     * <p>logback 的级别过滤早于 appender，不调级别就断言不到降级后的 DEBUG；{@link #close()} 恢复原级别
     * 并摘除 appender，避免污染其它用例。</p>
     */
    static final class LogCapture implements AutoCloseable {

        private final Logger logger;
        private final Level previousLevel;
        private final ListAppender<ILoggingEvent> appender;

        LogCapture() {
            this.logger = (Logger) LoggerFactory.getLogger(SseStreamBridge.class);
            this.previousLevel = logger.getLevel();
            this.appender = new ListAppender<>();
            appender.start();
            logger.setLevel(Level.DEBUG);
            logger.addAppender(appender);
        }

        List<ILoggingEvent> events() {
            return new ArrayList<>(appender.list);
        }

        List<ILoggingEvent> events(Level level) {
            return events().stream().filter(event -> event.getLevel() == level).toList();
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }
}

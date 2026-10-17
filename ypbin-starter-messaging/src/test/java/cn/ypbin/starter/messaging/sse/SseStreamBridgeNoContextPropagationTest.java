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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.FilteredClassLoader;
import reactor.core.publisher.Flux;

/**
 * 缺 {@code io.micrometer:context-propagation} 时的<strong>退化路径</strong>用例。
 *
 * <p>该依赖在 messaging 里是 <em>optional</em>：宿主可能完全没有它（例如纯 servlet 应用不引入任何
 * Reactor 观测能力）。此时 {@link SseStreamBridge} 必须<strong>照常工作、退化为不传播上下文、且不报错</strong>
 * ——不能因为「可选增强缺席」就让流式能力整体不可用，也不能抛 {@code NoClassDefFoundError}。</p>
 *
 * <p>本用例用 {@link FilteredClassLoader} 把 micrometer 的入口类<strong>真实藏起来</strong>再调用
 * {@link SseContextPropagator#micrometerOrNoop(ClassLoader)}，因此走的是真实的守卫分支，而不是
 * 把 NOOP 直接塞进去凑断言。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
class SseStreamBridgeNoContextPropagationTest {

    private static final long AWAIT_MILLIS = 5_000L;

    @Test
    @DisplayName("★ 隐藏 context-propagation：守卫应选中退化实现，且不抛 NoClassDefFoundError")
    void fallsBackToNoopWhenMicrometerContextPropagationMissing() {
        ClassLoader filtered = new FilteredClassLoader("io.micrometer.context");

        SseContextPropagator propagator = SseContextPropagator.micrometerOrNoop(filtered);

        assertThat(propagator).as("缺依赖时应退化为 NOOP 实现，而不是崩溃").isNotNull();
        SseContextPropagator.Snapshot snapshot = propagator.capture();
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.carriesContext()).as("退化实现不携带上下文").isFalse();
        assertThat(snapshot.enrich("marker")).as("退化实现不得改写 Context").isEqualTo("marker");
        // 作用域必须是可关闭的空实现：调用方 try-with-resources 不能炸
        try (SseContextPropagator.Snapshot.Scope scope = snapshot.restore()) {
            assertThat(scope).isNotNull();
        }
    }

    @Test
    @DisplayName("★ 使用退化传播器时流式桥接照常工作：逐帧发送 + 完成")
    void streamStillWorksWithNoopPropagator() throws Exception {
        SseStreamBridgeTest.RecordingEmitter emitter = new SseStreamBridgeTest.RecordingEmitter();
        SseStreamBridge bridge = SseStreamBridgeTest.bridgeFor(emitter, SseContextPropagator.micrometerOrNoop(
            new FilteredClassLoader("io.micrometer.context")));

        bridge.stream(Flux.just("a", "b"), SseStreamOptions.defaults());

        assertThat(emitter.awaitCompletion()).as("缺传播能力时流仍必须正常结束").isTrue();
        assertThat(emitter.fragments()).containsExactly("data:", "a", "\n\n", "data:", "b", "\n\n");
    }

    @Test
    @DisplayName("退化路径下错误处理仍完整：错误帧 + complete + 上游释放")
    void errorPathStillWorksWithNoopPropagator() throws Exception {
        SseStreamBridgeTest.RecordingEmitter emitter = new SseStreamBridgeTest.RecordingEmitter();
        SseStreamBridge bridge = SseStreamBridgeTest.bridgeFor(emitter,
            SseContextPropagator.micrometerOrNoop(new FilteredClassLoader("io.micrometer.context")));

        java.util.concurrent.atomic.AtomicBoolean disposed = new java.util.concurrent.atomic.AtomicBoolean(false);
        reactor.core.publisher.FluxSink<String>[] sinkRef = new reactor.core.publisher.FluxSink[1];
        reactor.core.publisher.Flux<String> upstream = reactor.core.publisher.Flux.<String>create(sink -> {
            sinkRef[0] = sink;
            // 错误终止走 sink.onDispose（doOnCancel 只在取消时触发，已实测）
            sink.onDispose(() -> disposed.set(true));
        });
        bridge.stream(upstream, SseStreamOptions.defaults());
        sinkRef[0].error(new IllegalStateException("模型超时"));

        assertThat(emitter.awaitCompletion()).isTrue();
        assertThat(emitter.fragments())
            .containsExactly("event:error\ndata:", "对话出错：模型超时", "\n\n");
        assertThat(disposed.get()).as("退化路径也必须释放上游订阅").isTrue();
    }

    @Test
    @DisplayName("退化路径不因缺类而产生 IOException/链接错误（端到端不抛）")
    void noLinkageErrorOnMissingOptionalDependency() throws Exception {
        SseStreamBridgeTest.RecordingEmitter emitter = new SseStreamBridgeTest.RecordingEmitter();
        SseStreamBridge bridge = SseStreamBridgeTest.bridgeFor(emitter,
            SseContextPropagator.micrometerOrNoop(new FilteredClassLoader("io.micrometer.context")));

        bridge.stream(Flux.just("x"), SseStreamOptions.defaults());

        assertThat(emitter.awaitCompletion()).isTrue();
        assertThat(emitter.fragments()).containsExactly("data:", "x", "\n\n");
    }
}

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

import org.springframework.util.ClassUtils;

/**
 * 调用线程「ThreadLocal 上下文」的捕获与还原接缝（<strong>包级可见的内部接缝，不是公开 API</strong>）。
 *
 * <p><strong>为什么需要它</strong>：{@link SseStreamBridge} 要把发起请求时的租户/用户等 ThreadLocal 上下文
 * 带过 Reactor 的线程切换，但承载该能力的 {@code io.micrometer:context-propagation} 在
 * {@code ypbin-starter-messaging} 里是 <strong>optional</strong> 依赖（见该模块 pom 注释）。若直接在这一层
 * 引用 micrometer 类型，缺该依赖的宿主一加载本模块就 {@code NoClassDefFoundError}。故把「捕获/还原」抽成
 * 一个只含本模块类型的小接缝：micrometer 在位时用 {@link MicrometerSseContextPropagator}，
 * 缺席时退化为 {@link Snapshot#NOOP}——<strong>不注入 Context、不还原线程，也不报错</strong>。</p>
 *
 * <p><strong>为什么用通用快照而不是 {@code ypbin-starter-extension-tenant} 的类型</strong>：本模块是 L1
 * 基础能力，租户模块是 L2 业务骨架；{@code LayeringRulesTest} 在字节码层禁止 L1 依赖
 * {@code cn.ypbin.starter.tenant..}（否则「按需引入」会退化为「引一个拖全部」）。而租户模块已把
 * {@code TenantThreadLocalAccessor} 注册进 micrometer 的全局 {@code ContextRegistry}，故本接缝取
 * <strong>通用</strong>快照即可：装了租户模块就自动带上租户上下文，没装就什么也不做——不产生对租户模块的
 * 编译期/运行期强依赖。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
interface SseContextPropagator {

    /**
     * micrometer {@code context-propagation} 的入口类全限定名：存在于 classpath 才启用快照传播。
     */
    String MICROMETER_CONTEXT_CLASS = "io.micrometer.context.ContextRegistry";

    /**
     * 抓取当前线程的上下文快照。
     *
     * <p>必须在<strong>发起请求的线程</strong>上调用（{@link SseStreamBridge} 在 {@code stream(...)}
     * 入口处捕获，早于 {@code subscribe()}）。</p>
     *
     * @return 快照；无传播能力时返回 {@link Snapshot#NOOP}（非 {@code null}）
     */
    Snapshot capture();

    /**
     * 一次流式响应期间复用的上下文快照。
     *
     * <p>实现须线程安全：同一个快照会被多个 Reactor 线程（模型回调线程、调度线程）并发读取。</p>
     */
    interface Snapshot {

        /** 不携带任何上下文、不做任何线程还原的快照（缺 context-propagation 依赖时使用）。 */
        Snapshot NOOP = new Snapshot() {

            @Override
            public boolean carriesContext() {
                return false;
            }

            @Override
            public <C> C enrich(C context) {
                return context;
            }

            @Override
            public Scope restore() {
                return Scope.NOOP;
            }
        };

        /**
         * 快照是否携带了内容（至少有一个已注册的 ThreadLocalAccessor 抓到了值）。
         *
         * <p><strong>为什么必须区分「空」</strong>：Reactor 有一个反直觉但已实测（reactor-core 3.8.7）的语义——
         * 链上出现 {@code contextWrite} 后，若其 lambda <strong>返回了与入参不同的 Context</strong>
         * （含返回 {@code Context.empty()} 这种「丢弃入参」的形态），Reactor 就<strong>不再</strong>在
         * {@code subscribe()} 时自动捕获调用线程的 ThreadLocal，宿主本已生效的自动传播随之被
         * <strong>掐断</strong>（静默劣化）。</p>
         *
         * <p><strong>措辞精确性（2026-10-16 独立验收纠正）</strong>：真正的触发条件是「lambda 不原样返回入参」，
         * <strong>不是</strong>「出现了 {@code contextWrite}」或「写进去的值是空的」。恒等 lambda（{@code c -> c}）
         * 与「写入键值但仍返回同一 Context」都不会抑制自动捕获；而本接口的空快照实现恰好会把入参替换为
         * 空 Context，故仍需由本方法把它挡掉。返回 {@code false} 时调用方必须跳过 {@code contextWrite}，
         * 只走 {@link #restore()} 这条显式通道。</p>
         *
         * @return 携带内容返回 true
         */
        boolean carriesContext();

        /**
         * 把快照并入 Reactor Context（供链上<strong>上游</strong>算子与自动传播使用）。
         *
         * <p>调用方须先确认 {@link #carriesContext()} 为 true，否则会掐断自动传播。</p>
         *
         * @param context 目标 Reactor Context（非空）
         * @param <C>     Context 类型
         * @return 并入后的 Context
         */
        <C> C enrich(C context);

        /**
         * 把快照还原到当前线程，返回<strong>备份-恢复</strong>作用域：{@code close()} 时把当前线程原有上下文
         * 精确还原（不是无条件清理，否则会污染调用者自身的请求上下文——实测 micrometer 的
         * {@code ContextSnapshot.Scope} 正是此语义）。
         *
         * @return 作用域，配合 try-with-resources 使用
         */
        Scope restore();

        /**
         * 还原作用域。
         */
        interface Scope extends AutoCloseable {

            /** 空作用域（无传播能力时使用）。 */
            Scope NOOP = () -> {
            };

            @Override
            void close();
        }
    }

    /**
     * 取得「micrometer 在位则用其实现，否则退化」的传播器。
     *
     * <p>类存在性用 {@link ClassUtils#isPresent(String, ClassLoader)} 判定（spring-core，始终在
     * classpath）：只有判定通过才会触及 {@link MicrometerSseContextPropagator}，因此缺
     * {@code context-propagation} 的宿主不会因类解析失败而崩。</p>
     *
     * @return 传播器（永不为 {@code null}）
     */
    static SseContextPropagator micrometerOrNoop() {
        return micrometerOrNoop(SseContextPropagator.class.getClassLoader());
    }

    /**
     * 按指定类加载器判定（包级可见：供测试用过滤类加载器真实复现「缺 context-propagation」的退化路径）。
     *
     * @param classLoader 用于判定类存在性的类加载器
     * @return 传播器（永不为 {@code null}）
     */
    static SseContextPropagator micrometerOrNoop(ClassLoader classLoader) {
        if (ClassUtils.isPresent(MICROMETER_CONTEXT_CLASS, classLoader)) {
            return new MicrometerSseContextPropagator();
        }
        return () -> Snapshot.NOOP;
    }
}

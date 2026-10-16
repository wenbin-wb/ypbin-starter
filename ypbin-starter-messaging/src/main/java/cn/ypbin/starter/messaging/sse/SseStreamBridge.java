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

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;

/**
 * {@code Flux<String>} → {@link SseEmitter} 的流式桥接：把「大模型逐 token 回调」这一类
 * Reactor 流，转成浏览器可直接消费的 SSE 响应。
 *
 * <p><strong>为什么收进 starter</strong>：{@code ypbin-starter-ai} 的 {@code AiChatService#chatStream(...)}
 * 返回 {@code Flux<String>}，但「Flux → SSE」的桥接属于通用传输能力，与具体模型无关；不收进基座，
 * 每个下游都要把同一段「逐帧发送 / 错误帧 / 订阅释放」抄一遍（IoT 此前就是自己手写了一遍）。本类与
 * {@link SseEmitterManager}／扇出／票据同在 messaging 的 {@code sse} 包，SSE 传输能力集中一处。</p>
 *
 * <p><strong>语义（与 IoT 既有实现逐条对齐，属对外契约）</strong>：</p>
 * <ul>
 *   <li>每个 token <strong>立即</strong>发送，不缓冲、不聚合（前端才能看到逐字输出）；</li>
 *   <li>正常结束调用 {@link SseEmitter#complete()}；</li>
 *   <li>出错时<strong>先发一帧错误事件再 complete</strong>，并以 {@code log.error} 记录<strong>完整堆栈</strong>
 *       （静默吞异常会让模型故障在生产里无迹可查）；错误文案 = {@link SseStreamOptions#errorPrefix()}
 *       + 根因消息，可用 {@link #rootMessage(Throwable)} 逻辑取到最深层原因；</li>
 *   <li><strong>客户端断开 / 超时必须 dispose 上游订阅</strong>：否则 Reactor 流仍在后台空转（连接已死、
 *       token 照发），既是资源泄漏也是纯浪费。本类注册 {@code onTimeout}/{@code onError}/{@code onCompletion}
 *       三条路径，且发送失败时也立即释放；</li>
 *   <li>默认<strong>不改变既有线上报文格式</strong>：默认发裸 {@code data:} 帧（等价于
 *       {@code emitter.send(token)}），只有显式配置 {@link SseStreamOptions#eventName()} 才会多写
 *       {@code event:} 行——IoT 前端按「无事件名即正常 token、{@code event:error} 才是错误」解析，
 *       默认值必须保持逐字节兼容。</li>
 * </ul>
 *
 * <p><strong>上下文传播（前置条件必须看清）</strong>：大模型的 token 由 Reactor 调度线程生产，而租户/用户
 * 上下文存在 {@code ThreadLocal} 里，跨线程默认会「失忆」。本类在 {@code stream(...)} 入口用
 * {@link SseContextPropagator} 抓取<strong>调用线程</strong>的上下文快照，并：</p>
 * <ol>
 *   <li>把快照并入 Reactor Context（供链上算子与自动传播使用）；</li>
 *   <li>在每个回调内用「备份-恢复」作用域把快照还原到当前线程（<strong>不依赖</strong>宿主是否调用了
 *       {@code Hooks.enableAutomaticContextPropagation()}）。</li>
 * </ol>
 * <p>前置条件与边界：</p>
 * <ul>
 *   <li>传播能力来自 <strong>micrometer {@code context-propagation} 的全局
 *       {@code ContextRegistry}</strong>，在 messaging 模块里是 <em>optional</em> 依赖。依赖缺席时
 *       退化为<strong>不传播、不报错</strong>（{@link SseContextPropagator#micrometerOrNoop()}）；</li>
 *   <li>{@code ypbin-starter-extension-tenant} 是<strong>可选</strong>模块，本类不引用它的任何类型
 *       （L1 不得依赖 L2，见 {@code LayeringRulesTest}）。租户模块自行把
 *       {@code TenantThreadLocalAccessor} 注册进 {@code ContextRegistry}，装了就自动带上租户上下文；</li>
 *   <li><strong>快照必须非空才写 Context</strong>：Reactor 有个反直觉语义（3.8.x 实测）——链上只要出现
 *       {@code contextWrite}，即使 lambda 是恒等函数，Reactor 也不再于 {@code subscribe()} 时自动捕获调用
 *       线程的 ThreadLocal。若用空快照去写 Context，会把宿主本来生效的自动传播<strong>掐断</strong>。
 *       故仅当 {@link SseContextPropagator.Snapshot#carriesContext()} 为 true 时才注入；</li>
 *   <li>快照在 {@code stream(...)} 调用线程上抓取，因此 <strong>{@code stream(...)} 必须在该请求线程内调用</strong>
 *       （不要在异步线程里再调）。</li>
 * </ul>
 *
 * <p><strong>业务逻辑不要上收</strong>：本类只负责<strong>传输</strong>。落库、会话统计、标题生成、
 * 用量埋点等属业务，应由调用方在自己的 Reactor 链上组合（见 {@link #stream(Flux, SseStreamOptions)}
 * 对算子顺序的说明），本类不提供任何业务回调。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
public class SseStreamBridge {

    private static final Logger log = LoggerFactory.getLogger(SseStreamBridge.class);

    private final SseContextPropagator contextPropagator;

    /**
     * 默认构造器：按 classpath 自动选择传播实现（有 {@code context-propagation} 则用，否则退化为不传播）。
     */
    public SseStreamBridge() {
        this(SseContextPropagator.micrometerOrNoop());
    }

    /**
     * 可注入传播实现的构造器（包级可见：仅测试用于固定传播行为，不属于公开 API）。
     *
     * @param contextPropagator 上下文传播器（非空）
     */
    SseStreamBridge(SseContextPropagator contextPropagator) {
        this.contextPropagator = Objects.requireNonNull(contextPropagator, "contextPropagator 不能为空");
    }

    /**
     * 以默认选项桥接（不设总超时、裸 {@code data:} 帧、错误事件名 {@code error}、开启上下文传播）。
     *
     * @param flux 上游流（如模型逐 token 输出），非空
     * @return 交给 Controller 返回给客户端的 {@link SseEmitter}
     */
    public SseEmitter stream(Flux<String> flux) {
        return stream(flux, SseStreamOptions.defaults());
    }

    /**
     * 以指定选项桥接。
     *
     * <p><strong>订阅与算子顺序</strong>：本方法在传入 {@code flux} 的<strong>下游</strong>追加
     * 「逐帧发送 → 错误处理 → 完成」三个回调，并把上下文快照写在<strong>整条链的最底部</strong>。因此：</p>
     * <ul>
     *   <li>调用方自己的业务算子（累计内容、落库、统计）留在传入的 {@code flux} 上即可，
     *       它们位于本类回调的<strong>上游</strong>，会在 {@code emitter.complete()} <strong>之前</strong>执行
     *       （这正是业务需要的：完成前落库，失败时还能走错误帧）；</li>
     *   <li>业务算子同样位于 {@code contextWrite} 之上，故可享受上下文传播（宿主开启了 Reactor 自动传播时）。</li>
     * </ul>
     *
     * @param flux    上游流，非空
     * @param options 桥接选项，非空
     * @return 交给 Controller 返回给客户端的 {@link SseEmitter}
     */
    public SseEmitter stream(Flux<String> flux, SseStreamOptions options) {
        Objects.requireNonNull(flux, "flux 不能为空");
        Objects.requireNonNull(options, "options 不能为空");

        SseEmitter emitter = createEmitter(options);
        SseContextPropagator.Snapshot snapshot = options.propagateContext()
            ? contextPropagator.capture()
            : SseContextPropagator.Snapshot.NOOP;

        // 用 Disposables.swap() 而不是 AtomicReference：回调有可能在 subscribe() 返回之前就被触发
        // （例如流在订阅期同步抛错/同步完成）。swap 在「先 dispose、后 update」时会立即 dispose 新来的
        // 订阅，从而关掉这个竞态窗口；AtomicReference 会先读到 null 而漏掉释放。
        Disposable.Swap subscription = Disposables.swap();

        // 客户端断开 / 容器超时 → 释放上游订阅（否则 Reactor 流继续空转，连接已死还照发 token）
        emitter.onTimeout(() -> {
            log.debug("[ypbin-starter] SSE 连接超时，释放上游流式订阅");
            subscription.dispose();
        });
        emitter.onError(error -> {
            log.debug("[ypbin-starter] SSE 连接异常，释放上游流式订阅", error);
            subscription.dispose();
        });
        // 完成回调同样释放：覆盖「客户端主动断开后容器判定完成」等不触发 onError 的路径，幂等无害
        emitter.onCompletion(subscription::dispose);

        Flux<String> pipeline = flux
            .doOnNext(token -> sendToken(emitter, token, options, snapshot, subscription))
            .doOnError(error -> failStream(emitter, error, options, snapshot, subscription))
            .doOnComplete(() -> {
                runInContext(snapshot, emitter::complete);
                subscription.dispose();
            });

        if (snapshot.carriesContext()) {
            // 仅非空快照才写 Context：空快照会抑制 Reactor 订阅期的自动捕获（见类注释）
            pipeline = pipeline.contextWrite(context -> snapshot.enrich(context));
        }

        subscription.update(pipeline.subscribe());
        return emitter;
    }

    /**
     * 构造一份「只带错误帧」的 SSE 响应：立即发送 {@code event:error} 帧、随即 complete。
     *
     * <p>用于<strong>尚未开始流式输出</strong>就已知失败的场景（如 AI 模块未启用、参数校验未过），
     * 让前端走统一的 {@code onError} 分支，而不是拿到一个空的 200 流。</p>
     *
     * @param message 错误文案（原样发出，不加前缀）
     * @return 已完成的 {@link SseEmitter}
     */
    public static SseEmitter errorEmitter(String message) {
        return errorEmitter(message, SseStreamOptions.defaults());
    }

    /**
     * 构造一份「只带错误帧」的 SSE 响应（可定制超时与错误事件名）。
     *
     * @param message 错误文案（原样发出，不加前缀）
     * @param options 桥接选项（使用其 {@link SseStreamOptions#timeoutMillis()} 与
     *                {@link SseStreamOptions#errorEventName()}）
     * @return 已完成的 {@link SseEmitter}
     */
    public static SseEmitter errorEmitter(String message, SseStreamOptions options) {
        Objects.requireNonNull(message, "message 不能为空");
        Objects.requireNonNull(options, "options 不能为空");
        SseEmitter emitter = new SseEmitter(options.timeoutMillis());
        sendErrorFrame(emitter, message, options);
        emitter.complete();
        return emitter;
    }

    /**
     * 创建 {@link SseEmitter}。
     *
     * <p>包级可见的实例方法：与 {@link SseEmitterManager#newEmitter()} 同一约定——测试继承本类覆写
     * 本方法即可注入记录型替身（断言真实发帧内容、复现客户端断开/超时回调），生产路径始终返回真实
     * {@code SseEmitter}。包级可见不属于公开 API（api-compat 门禁只比对 public/protected）。</p>
     *
     * @param options 选项（提供连接超时）
     * @return 新建的 emitter
     */
    SseEmitter createEmitter(SseStreamOptions options) {
        return new SseEmitter(options.timeoutMillis());
    }

    /**
     * 向指定 emitter 发送错误帧（{@code event:<errorEventName>} + 文案）。发送失败只记 WARN——连接多半已断，
     * 调用方拿到的仍应是一个已结束的 emitter，而不是被异常打断。
     *
     * <p>包级可见：{@link #errorEmitter(String, SseStreamOptions)} 与流式失败路径共用本方法，
     * 测试可传入记录型 emitter 直接断言真实帧内容。</p>
     *
     * @param emitter 目标 emitter
     * @param message 文案
     * @param options 选项
     */
    static void sendErrorFrame(SseEmitter emitter, String message, SseStreamOptions options) {
        try {
            emitter.send(errorFrame(message, options));
        } catch (Exception e) {
            log.warn("[ypbin-starter] SSE 错误帧发送失败", e);
        }
    }

    /**
     * 构造正常 token 的事件帧：默认裸 {@code data:} 帧（与 {@code emitter.send(token)} 逐字节一致），
     * 配置了 {@link SseStreamOptions#eventName()} 时额外写 {@code event:} 行。
     *
     * <p>包级可见：供测试直接断言「发出去的字节长什么样」——SSE 报文是对外契约，靠字符串拼接断言比
     * 依赖容器更适合锁定它。</p>
     *
     * @param token   令牌（非空）
     * @param options 选项
     * @return 事件帧构造器
     */
    static SseEmitter.SseEventBuilder tokenFrame(String token, SseStreamOptions options) {
        if (options.eventName().isEmpty()) {
            return SseEmitter.event().data(token);
        }
        return SseEmitter.event().name(options.eventName()).data(token);
    }

    /**
     * 构造错误事件帧（{@code event:<errorEventName>} + 文案）。
     *
     * @param message 错误文案（非空）
     * @param options 选项
     * @return 事件帧构造器
     */
    static SseEmitter.SseEventBuilder errorFrame(String message, SseStreamOptions options) {
        return SseEmitter.event().name(options.errorEventName()).data(message);
    }

    /**
     * 提取异常链<strong>最深层</strong>原因的消息文本；无消息时退化为异常类简单名。
     *
     * <p>模型/HTTP 客户端常把真实原因层层包裹，只报最外层往往只剩 {@code "500 Internal Server Error"}
     * 之类没有信息量的文案。</p>
     *
     * @param error 异常（非空）
     * @return 根因文案（非空）
     */
    public static String rootMessage(Throwable error) {
        Objects.requireNonNull(error, "error 不能为空");
        Throwable current = error;
        @Nullable Throwable cause = current.getCause();
        while (cause != null) {
            current = cause;
            cause = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    /**
     * 发送单个 token；发送失败即认为客户端已断开，立即释放上游订阅并结束响应。
     */
    private static void sendToken(SseEmitter emitter, String token, SseStreamOptions options,
                                  SseContextPropagator.Snapshot snapshot, Disposable.Swap subscription) {
        runInContext(snapshot, () -> {
            try {
                emitter.send(tokenFrame(token, options));
            } catch (Exception e) {
                // 发送失败基本都是客户端已断开：释放上游订阅，避免 Reactor 流继续空转
                log.warn("[ypbin-starter] SSE 流式帧发送失败，释放上游订阅", e);
                subscription.dispose();
                emitter.complete();
            }
        });
    }

    /**
     * 处理上游失败：记录完整堆栈 → 发送错误帧 → 释放订阅 → 结束响应。
     */
    private static void failStream(SseEmitter emitter, Throwable error, SseStreamOptions options,
                                   SseContextPropagator.Snapshot snapshot, Disposable.Swap subscription) {
        runInContext(snapshot, () -> {
            // 必须传完整堆栈：吞异常/只留 message 会让生产故障无从排查
            log.error("[ypbin-starter] SSE 流式响应失败，将发送错误帧并释放上游订阅", error);
            if (options.errorEventEnabled()) {
                sendErrorFrame(emitter, options.errorPrefix() + rootMessage(error), options);
            }
            subscription.dispose();
            emitter.complete();
        });
    }

    /**
     * 在上下文快照作用域内执行动作：{@code close()} 时把当前线程原有上下文精确还原
     * （备份-恢复，不会污染调用者自身的请求上下文）。
     *
     * <p>无传播能力时 {@link SseContextPropagator.Snapshot#restore()} 返回空作用域，等价于直接执行。</p>
     *
     * @param snapshot 快照
     * @param action   动作
     */
    private static void runInContext(SseContextPropagator.Snapshot snapshot, Runnable action) {
        try (SseContextPropagator.Snapshot.Scope scope = snapshot.restore()) {
            action.run();
        }
    }
}

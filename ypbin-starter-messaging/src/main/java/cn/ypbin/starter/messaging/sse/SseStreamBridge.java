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

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
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
 *   <li>出错时<strong>先发一帧错误事件再 complete</strong>，错误文案 = {@link SseStreamOptions#errorPrefix()}
 *       + 根因消息（取法见 {@link #rootMessage(Throwable)}），并按<strong>异常形态分级</strong>记录：
 *       <ul>
 *         <li><strong>服务端真实故障</strong>：{@code log.error} + <strong>完整堆栈</strong> + 错误帧
 *             （静默吞异常会让模型故障在生产里无迹可查）；</li>
 *         <li><strong>客户端主动断开 / 连接已不可用</strong>：<strong>上游错误</strong>只按
 *             <strong>类型/类名</strong>判定（{@link AsyncRequestNotUsableException}、Tomcat
 *             {@code ClientAbortException}，见 {@link #isClientDisconnectFromUpstream(Throwable)}）；
 *             {@code Broken pipe} / {@code Connection reset} 一类的<strong>消息启发式只用于发送路径</strong>
 *             （见 {@link #isClientDisconnectOnWrite(Throwable)}）——上游抛 {@code IOException
 *             ("Connection reset")} 可能是模型 API / 下游 HTTP 重置连接，属真实故障，不得静默。
 *             命中后降级为 {@code log.debug}（<strong>不带堆栈</strong>）并<strong>跳过错误帧</strong>——
 *             向已断开的连接补发必然再失败一次，只会把「正常断开」刷成 ERROR/WARN 监控告警。
 *             响应仍会 complete，不影响释放语义；</li>
 *       </ul></li>
 *   <li><strong>终止错误由订阅者消费</strong>（{@code subscribe(..., errorConsumer)}）：{@code doOnError}
 *       只是副作用算子、并不消费信号，若订阅时不提供 errorConsumer，Reactor 会走
 *       {@code onErrorDropped} 全局兜底（日志 {@code Operator called default onErrorDropped}），
 *       既丢失请求上下文也误导监控。本类在订阅端统一处理错误（分级记录 + 错误帧 + complete + 释放订阅），
 *       错误信号恰好被消费一次、不再冒泡到全局钩子；</li>
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

    /**
     * Tomcat 客户端中止异常类名。
     *
     * <p>messaging 只依赖 {@code spring-webmvc}（optional），Servlet 容器实现不在编译期 classpath，故按
     * <strong>类名</strong>匹配（不硬依赖 Tomcat）。实测 tomcat-embed-core 11.0.24 中
     * {@code ClientAbortException extends org.apache.coyote.BadRequestException extends IOException}，
     * 且存在无参/纯 {@code String} 构造器——消息里未必带 {@code Broken pipe}，仅靠消息规则会漏判，
     * 故保留显式类名判定。</p>
     */
    private static final String CLIENT_ABORT_EXCEPTION_CLASS_NAME =
        "org.apache.catalina.connector.ClientAbortException";

    /**
     * 连接已被对端关闭的典型异常消息片段（统一小写后做包含匹配）。
     *
     * <p><strong>该启发式只用于「发送路径」的失败判定</strong>（{@link #isClientDisconnectOnWrite(Throwable)}）：
     * 往已断开的连接写帧失败，消息基本就是这些片段。它<strong>不得</strong>用于上游错误——上游
     * {@code IOException("Connection reset")} 很可能是模型 API / 下游 HTTP 服务端重置连接（真实故障），
     * 用消息判定会把它静默降级。消息匹配天然是启发式（跨 JVM/OS 文案不一致），未命中时走保守的
     * ERROR/WARN 路径，宁可多一条堆栈也不把真实故障降级。</p>
     */
    private static final List<String> DISCONNECT_MESSAGE_PATTERNS = List.of(
        "broken pipe",
        "connection reset",
        "connection aborted",
        "connection was aborted");

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
     * 「逐帧发送 → 完成」两个算子回调，并在<strong>订阅端</strong>（errorConsumer）消费终止错误
     * （分级记录 → 错误帧 → complete → 释放订阅），最后把上下文快照写在<strong>整条链的最底部</strong>。
     * 因此：</p>
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
            .doOnComplete(() -> {
                runInContext(snapshot, emitter::complete);
                subscription.dispose();
            });

        if (snapshot.carriesContext()) {
            // 仅非空快照才写 Context：空快照会抑制 Reactor 订阅期的自动捕获（见类注释）
            pipeline = pipeline.contextWrite(context -> snapshot.enrich(context));
        }

        // 终止错误必须由订阅者消费：doOnError 只是副作用算子、不消费信号，沿用无 errorConsumer 的
        // subscribe() 重载时，错误会冒泡到 Reactor 的 onErrorDropped 全局兜底
        // （日志 "Operator called default onErrorDropped"），既丢请求上下文也误导监控。
        // 这里显式传入 errorConsumer（onNext 为 null 是该重载的合法用法），错误恰好被消费一次。
        subscription.update(pipeline.subscribe(null,
            error -> failStream(emitter, error, options, snapshot, subscription)));
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
     * 向指定 emitter 发送错误帧（{@code event:<errorEventName>} + 文案）。
     *
     * <p>发送失败即说明连接已不可用：客户端断开类异常（{@link #isClientDisconnectOnWrite(Throwable)}）只记
     * DEBUG（连接已死，再打完整堆栈纯属噪声）；其余异常记 WARN + 完整堆栈（发送链路自身异常，不得静默）。
     * 两种情况下调用方拿到的仍应是一个已结束的 emitter，而不是被异常打断。</p>
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
            if (isClientDisconnectOnWrite(e)) {
                log.debug("[ypbin-starter] SSE 错误帧发送失败（客户端已断开：{}）", rootMessage(e));
            } else {
                log.warn("[ypbin-starter] SSE 错误帧发送失败", e);
            }
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
     *
     * <p>断开类异常降级 DEBUG（不带堆栈），其余异常保留 WARN + 完整堆栈。发送路径可以用
     * {@code Broken pipe} / {@code Connection reset} 这类消息启发式（见
     * {@link #isClientDisconnectOnWrite(Throwable)}）：往已断开的连接写帧失败，与「上游服务端重置连接」
     * 是两回事。</p>
     */
    private static void sendToken(SseEmitter emitter, String token, SseStreamOptions options,
                                  SseContextPropagator.Snapshot snapshot, Disposable.Swap subscription) {
        runInContext(snapshot, () -> {
            try {
                emitter.send(tokenFrame(token, options));
            } catch (Exception e) {
                // 发送失败基本都是客户端已断开：释放上游订阅，避免 Reactor 流继续空转
                if (isClientDisconnectOnWrite(e)) {
                    log.debug("[ypbin-starter] SSE 流式帧发送失败（客户端已断开：{}），释放上游订阅",
                        rootMessage(e));
                } else {
                    log.warn("[ypbin-starter] SSE 流式帧发送失败，释放上游订阅", e);
                }
                subscription.dispose();
                emitter.complete();
            }
        });
    }

    /**
     * 处理上游失败：按异常形态分级 → 释放订阅 → 结束响应。
     *
     * <ul>
     *   <li><strong>客户端断开 / 连接不可用</strong>（{@link #isClientDisconnectFromUpstream(Throwable)} 命中）：
     *       {@code log.debug} 低噪声、不带堆栈，并<strong>跳过错误帧</strong>（连接已死，补发只会再失败）；</li>
     *   <li><strong>服务端真实故障</strong>：{@code log.error} + 完整堆栈 + 错误帧（契约不变）。</li>
     * </ul>
     *
     * <p><strong>上游判定只认类型/类名，不用消息启发式</strong>：上游 {@code IOException("Connection
     * reset")} 可能是模型 API / 下游 HTTP 服务端重置连接，属真实故障，必须 ERROR + 完整堆栈 + 错误帧；
     * 若用消息把它当「客户端断开」，客户端会收到一条被截断却「正常结束」的流，缺陷被静默。</p>
     *
     * <p>无论哪条分支都会 {@code dispose()} 上游订阅并 {@code complete()}，保证「错误路径不泄漏、不挂死」。</p>
     */
    private static void failStream(SseEmitter emitter, Throwable error, SseStreamOptions options,
                                   SseContextPropagator.Snapshot snapshot, Disposable.Swap subscription) {
        runInContext(snapshot, () -> {
            if (isClientDisconnectFromUpstream(error)) {
                log.debug("[ypbin-starter] SSE 客户端已断开，跳过错误帧并释放上游订阅：{}", rootMessage(error));
            } else {
                // 必须传完整堆栈：吞异常/只留 message 会让生产故障无从排查
                log.error("[ypbin-starter] SSE 流式响应失败，将发送错误帧并释放上游订阅", error);
                if (options.errorEventEnabled()) {
                    sendErrorFrame(emitter, options.errorPrefix() + rootMessage(error), options);
                }
            }
            subscription.dispose();
            emitter.complete();
        });
    }

    /**
     * 判定<strong>上游错误</strong>是否为「客户端已断开 / 连接已不可用」——命中后按低噪声处理
     * （DEBUG、不带堆栈、跳过错误帧）。沿 cause 链逐层判定，命中任一即算：
     * <ol>
     *   <li>{@link AsyncRequestNotUsableException}（spring-web，容器异步请求已不可用）；</li>
     *   <li>Tomcat {@code ClientAbortException}（按类名，见 {@link #CLIENT_ABORT_EXCEPTION_CLASS_NAME}）。</li>
     * </ol>
     *
     * <p><strong>刻意不包含消息启发式</strong>（与 {@link #isClientDisconnectOnWrite(Throwable)} 的关键差异）：
     * 上游服务端（模型 API、下游 HTTP）重置连接同样会抛 {@code IOException("Connection reset")}，
     * 按消息判定会把真实故障降级成 DEBUG 且不发错误帧，客户端只看到一条被截断却「正常结束」的流。
     * 未命中时走保守路径（ERROR + 完整堆栈 + 错误帧）。</p>
     *
     * @param error 上游异常（非空）
     * @return 是否属于客户端断开/连接不可用
     */
    static boolean isClientDisconnectFromUpstream(Throwable error) {
        return matchesClientDisconnect(error, false);
    }

    /**
     * 判定<strong>发送路径失败</strong>是否为「客户端已断开 / 连接已不可用」——用于
     * {@code sendToken}/{@code sendErrorFrame}。除类型/类名（同
     * {@link #isClientDisconnectFromUpstream(Throwable)}）外，额外接受
     * {@link IOException} 的<strong>消息启发式</strong>：往已断开的连接写帧失败时，异常消息基本就是
     * {@link #DISCONNECT_MESSAGE_PATTERNS} 里的 {@code Broken pipe} / {@code Connection reset} 片段，
     * 而发送路径不存在「上游服务端重置连接」这一歧义。</p>
     *
     * @param error 发送失败异常（非空）
     * @return 是否属于客户端断开/连接不可用
     */
    static boolean isClientDisconnectOnWrite(Throwable error) {
        return matchesClientDisconnect(error, true);
    }

    /**
     * 沿 cause 链判定「客户端断开」形态。
     *
     * @param error              异常（非空）
     * @param useMessageHeuristic 是否启用 {@link IOException} 消息启发式（发送路径 true，上游 false）
     * @return 是否命中
     */
    private static boolean matchesClientDisconnect(Throwable error, boolean useMessageHeuristic) {
        Objects.requireNonNull(error, "error 不能为空");
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof AsyncRequestNotUsableException) {
                return true;
            }
            if (CLIENT_ABORT_EXCEPTION_CLASS_NAME.equals(current.getClass().getName())) {
                return true;
            }
            if (useMessageHeuristic && current instanceof IOException && matchesDisconnectMessage(current.getMessage())) {
                return true;
            }
            if (current == current.getCause()) {
                // 自引用 cause（异常链成环）：防止死循环
                break;
            }
        }
        return false;
    }

    /** 消息是否含「连接已被对端关闭」的典型片段（小写包含匹配）。 */
    private static boolean matchesDisconnectMessage(@Nullable String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        for (String pattern : DISCONNECT_MESSAGE_PATTERNS) {
            if (lower.contains(pattern)) {
                return true;
            }
        }
        return false;
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

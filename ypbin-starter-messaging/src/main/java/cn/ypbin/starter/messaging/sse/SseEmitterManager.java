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
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 连接管理器。
 *
 * <p>维护「扇出目标键 → 该目标的多个 SSE 连接」注册表，（默认维度 userId），支持同一目标多端/多标签页同时在线。负责创建
 * 连接、注册完成/超时/异常时自动摘除，以及向指定目标或全体推送。默认扇出维度是 <strong>userId</strong>
 * （对既有调用方透明）；业务可通过 {@link SseAudienceResolver} 追加「租户/分组/任意 audience」等维度，
 * 用 {@link #connectAudience}/{@link #sendToAudience} 收发。</p>
 *
 * <p>连接对象是内存态，单实例内自洽。多实例部署时，{@link #sendToUser}/{@link #sendToAudience}/
 * {@link #broadcast} 会<strong>同时</strong>做两件事：① 直接投本实例的连接；② 经 {@link SseFanoutBridge}
 * （存在时）把消息发布到 Redis Pub/Sub 频道，其它实例的订阅方收到后投给各自的连接，且丢弃自己发的帧
 * 以避免回环。桥不存在（无 Redis）时退化为纯单实例投递，行为与此前完全一致。</p>
 *
 * <p>推送路径通过虚拟线程执行器异步分发，每个连接的发送互相隔离，一个慢客户端不会阻塞其他连接或调用方线程。</p>
 *
 * @author wenbin
 * @since 2026-08-15
 */
public class SseEmitterManager implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterManager.class);

    /** 用户 → 其所有 SSE 连接（同一用户可多端在线） */
    private final Map<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    private final long timeoutMillis;

    /** 心跳间隔（毫秒），<=0 表示关闭心跳 */
    private final long heartbeatIntervalMillis;

    /** 心跳调度器（懒创建，daemon 线程不阻塞关闭）；所有连接共享 */
    @Nullable
    private volatile ScheduledExecutorService scheduler;

    /** 发送执行器：每次推送在独立虚拟线程里执行，慢客户端不阻塞其他连接 */
    private final ExecutorService sendExecutor;

    /**
     * 跨实例扇出桥；无 Redis 时为 {@code null}（退化为纯单实例投递）。
     *
     * <p>用 {@code volatile} 而非 final：{@link SseAutoConfiguration} 存在「管理器先注册、桥后注册」的
     * 循环依赖形态（管理器需要在构造时拿到桥来发布，而桥需要管理器来投递本地连接），故提供
     * {@link #setFanoutBridge} 供自动配置在桥装配后回填。</p>
     */
    @Nullable
    private volatile SseFanoutBridge fanoutBridge;

    /**
     * 载荷 codec：桥不存在时用不到，但始终有默认实现（避免可空判断散落各处）。
     *
     * <p><strong>final 且无 setter</strong>：发布侧（{@link #publishFanout}）与订阅侧
     * （{@link SseFanoutSubscriber} 经 {@link #getCodec()}）必须用同一个实例，否则帧里的 codecId
     * 对不上、跨实例帧会被整批丢弃；构造后可变会让这个不变量失效。</p>
     */
    private final SseFanoutCodec codec;

    /**
     * 单实例构造器（无跨实例扇出，行为与此前完全一致）。
     *
     * @param timeoutMillis          连接超时（毫秒），0 表示不设总超时
     * @param heartbeatIntervalSeconds 心跳间隔（秒），<=0 关闭心跳
     */
    public SseEmitterManager(long timeoutMillis, long heartbeatIntervalSeconds) {
        this(timeoutMillis, heartbeatIntervalSeconds, null, new TextSseFanoutCodec());
    }

    /**
     * 可跨实例扇出的构造器（新增重载，既有签名保持不变）。
     *
     * @param timeoutMillis          连接超时（毫秒），0 表示不设总超时
     * @param heartbeatIntervalSeconds 心跳间隔（秒），<=0 关闭心跳
     * @param fanoutBridge           跨实例扇出桥，可为 {@code null}（纯单实例）
     * @param codec                  扇出载荷编解码器
     */
    public SseEmitterManager(long timeoutMillis, long heartbeatIntervalSeconds,
                             @Nullable SseFanoutBridge fanoutBridge, SseFanoutCodec codec) {
        this.timeoutMillis = timeoutMillis;
        this.heartbeatIntervalMillis = Math.max(0, heartbeatIntervalSeconds) * 1000L;
        this.sendExecutor = buildSendExecutor();
        this.fanoutBridge = fanoutBridge;
        this.codec = codec;
    }

    /**
     * 回填跨实例扇出桥（自动配置在桥装配后调用；桥 Bean 自身依赖本管理器的本地投递能力，
     * 两者互相依赖，故用 setter 打破构造期循环）。
     *
     * @param fanoutBridge 扇出桥，可为 {@code null}
     */
    public void setFanoutBridge(@Nullable SseFanoutBridge fanoutBridge) {
        this.fanoutBridge = fanoutBridge;
    }

    /**
     * 当前扇出载荷编解码器（发布侧与订阅侧<strong>唯一</strong>的 codec 来源）。
     *
     * <p>发布时写进帧的 {@code codecId} 与 {@link SseFanoutSubscriber} 的比对/解码都取这里，故
     * 「管理器与订阅者用不同 codec ⇒ 跨实例帧被整批丢弃」在结构上不可能发生。方法声明为
     * {@code final} 是有意的：子类覆盖它会重新打开这个缺陷；同理本类<strong>不提供</strong>
     * codec setter——运行期换 codec 会让已发布帧与新解码器错配，需要换请在构造时传入（4 参构造器）。</p>
     *
     * @return codec
     */
    public final SseFanoutCodec getCodec() {
        return codec;
    }

    private static ExecutorService buildSendExecutor() {
        return Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("ypbin-sse-send-", 0).factory());
    }

    /**
     * 为指定用户建立一个 SSE 连接并注册。
     *
     * <p>建连后启动心跳（定期发送 {@code : ping} 注释帧）保活中间代理、尽早暴露死连接；心跳发送失败
     * 即回收连接。连接完成/超时/异常时取消心跳并摘除。</p>
     *
     * @param userId 用户标识
     * @return 新建的 SseEmitter，交给 Controller 返回给客户端
     */
    public SseEmitter connect(String userId) {
        return connectAudience(userId);
    }

    /**
     * 为指定扇出维度建立一个 SSE 连接并注册（新增 API；{@link #connect(String)} 的特例是
     * {@code audience = userId}）。
     *
     * <p>语义与 {@link #connect(String)} 完全一致，只是注册键换成任意 audience。业务若要把一条连接
     * 同时挂到多个维度（如「该用户」+「该用户所属租户」），自行多次调用即可。</p>
     *
     * @param audience 扇出目标键（非空；建议带类型前缀避免不同维度撞键，如 {@code "tenant:7"}）
     * @return 新建的 SseEmitter，交给 Controller 返回给客户端
     */
    public SseEmitter connectAudience(String audience) {
        SseEmitter emitter = newEmitter();
        emitters.computeIfAbsent(audience, k -> ConcurrentHashMap.newKeySet()).add(emitter);

        ScheduledFuture<?> heartbeat = startHeartbeat(audience, emitter);
        emitter.onCompletion(() -> {
            cancelHeartbeat(heartbeat);
            remove(audience, emitter);
        });
        emitter.onTimeout(() -> {
            cancelHeartbeat(heartbeat);
            remove(audience, emitter);
        });
        emitter.onError(e -> {
            cancelHeartbeat(heartbeat);
            remove(audience, emitter);
        });
        // 建连即发一条注释帧，触发浏览器 EventSource onopen，并尽早暴露断连
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            remove(audience, emitter);
        }
        return emitter;
    }

    /**
     * 启动心跳任务：每隔 {@code heartbeatIntervalMillis} 发送一条 {@code : ping} 注释帧。
     *
     * <p>发送成功说明连接存活（同时保活中间代理）；失败说明连接已死，取消自身并回收连接。
     * 心跳无法重置容器异步总超时（配了有限 {@code timeout} 时到点仍会回收，但回收已静默化）。</p>
     *
     * @param audience 扇出目标键
     * @param emitter  连接
     * @return 心跳任务（未启用时返回 {@code null}）
     */
    @Nullable
    private ScheduledFuture<?> startHeartbeat(String audience, SseEmitter emitter) {
        if (heartbeatIntervalMillis <= 0) {
            return null;
        }
        ScheduledFuture<?>[] self = new ScheduledFuture<?>[1];
        ScheduledFuture<?> future = scheduler().scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                // 保留 debug 且不带堆栈：心跳失败绝大多数是客户端已断开（每次断开必现，属高频预期降级），
                // 升 WARN 会按连接数刷屏；连接回收本身是正确处置，不影响功能正确性
                log.debug("[ypbin-starter] SSE 心跳失败，回收连接：audience={}", audience, e);
                if (self[0] != null) {
                    self[0].cancel(false);
                }
                remove(audience, emitter);
            }
        }, heartbeatIntervalMillis, heartbeatIntervalMillis, TimeUnit.MILLISECONDS);
        self[0] = future;
        return future;
    }

    /**
     * 心跳调度器（懒创建单例）。
     *
     * <p>JDK 21 虚拟线程工厂：心跳任务以 I/O 阻塞（慢客户端写入）为主，虚拟线程可弹性承载，
     * 无需预设固定线程数；保留 daemon 语义由虚拟线程工厂的 thread builder 设置。</p>
     *
     * @return 调度器
     */
    private ScheduledExecutorService scheduler() {
        ScheduledExecutorService s = scheduler;
        if (s == null) {
            synchronized (this) {
                s = scheduler;
                if (s == null) {
                    Thread.Builder.OfVirtual builder = Thread.ofVirtual().name("ypbin-sse-heartbeat-", 0);
                    s = Executors.newScheduledThreadPool(1, builder.factory());
                    scheduler = s;
                }
            }
        }
        return s;
    }

    /**
     * 取消心跳任务。
     *
     * @param heartbeat 心跳任务（可为 {@code null}）
     */
    private static void cancelHeartbeat(@Nullable ScheduledFuture<?> heartbeat) {
        if (heartbeat != null) {
            heartbeat.cancel(false);
        }
    }

    /**
     * 心跳是否启用（间隔配置大于 0）。
     *
     * @return 启用返回 true
     */
    boolean isHeartbeatEnabled() {
        return heartbeatIntervalMillis > 0;
    }

    /**
     * 为多个扇出目标批量建连（同一订阅挂多个维度时用），返回第一条连接供 Controller 响应。
     *
     * <p><strong>为什么允许多条</strong>：一次订阅若同时属于「用户本人」与「所属租户」，客户端需要两类
     * 事件都能收到；注册表是「一键 → 多条连接」，故按目标各建一条。若按"一条连接挂多键"实现，
     * 断连回调与心跳归属会变模糊。</p>
     *
     * @param audiences 扇出目标键集合（非空；调用方须保证）
     * @return 第一条连接（HTTP 响应只能带一个 emitter；其余连接在服务端存活，按各自键路由事件）
     */
    public SseEmitter connectAll(Set<String> audiences) {
        SseEmitter first = null;
        for (String audience : audiences) {
            SseEmitter emitter = connectAudience(audience);
            if (first == null) {
                first = emitter;
            }
        }
        if (first == null) {
            // 调用方契约要求非空集合；真出现空集合时抛异常而不是静默建一条 userId 连接（避免掩盖调用错误）
            throw new IllegalArgumentException("audiences 不能为空");
        }
        return first;
    }

    /**
     * 向指定用户的所有连接推送，并（存在扇出桥时）跨实例扇出。
     *
     * <p>每条连接在独立虚拟线程里发送，互相隔离，慢客户端不阻塞其他连接。跨实例部分：本地连接由本方法
     * 直接投递；其它实例的连接由订阅方投递，订阅方会丢弃本实例发布的帧（避免回环重复投递）。</p>
     *
     * @param userId    用户标识（扇出目标键）
     * @param eventName 事件名
     * @param data      载荷
     */
    public void sendToUser(String userId, String eventName, Object data) {
        sendToAudience(userId, eventName, data);
    }

    /**
     * 向指定扇出维度的所有连接推送，并跨实例扇出（新增 API，既有签名不受影响）。
     *
     * <p>{@code audience} 是任意扇出目标键，例如 {@code "user:42"}、{@code "tenant:7"}；
     * {@link #sendToUser(String, String, Object)} 就是 {@code audience = userId} 的特例。</p>
     *
     * @param audience  扇出目标键
     * @param eventName 事件名
     * @param data      载荷
     */
    public void sendToAudience(String audience, String eventName, Object data) {
        dispatchLocal(audience, eventName, data);
        publishFanout(eventName, audience, data);
    }

    /**
     * 向全体在线连接广播，并跨实例扇出。
     *
     * <p>每条连接在独立虚拟线程里发送，互相隔离，慢客户端不阻塞其他连接。</p>
     *
     * @param eventName 事件名
     * @param data      载荷
     */
    public void broadcast(String eventName, Object data) {
        emitters.forEach((audience, set) ->
            set.forEach(emitter ->
                sendExecutor.execute(() -> doSend(audience, emitter, eventName, data))));
        publishFanout(eventName, null, data);
    }

    /**
     * 把其它实例扇出过来的消息投给<strong>本实例</strong>的连接（由 {@link SseFanoutSubscriber} 调用）。
     *
     * <p><strong>不再发布</strong>（否则会形成回环风暴）：只做本地投递。载荷由订阅方用同一 codec 解码后传入。</p>
     *
     * @param message 已解码的扇出消息
     * @param data    已解码的载荷
     */
    public void deliverLocal(SseFanoutMessage message, @Nullable Object data) {
        if (message.broadcast()) {
            emitters.forEach((audience, set) ->
                set.forEach(emitter ->
                    sendExecutor.execute(() -> doSend(audience, emitter, message.event(), data))));
            return;
        }
        String audience = message.audience();
        if (audience == null) {
            return;
        }
        Set<SseEmitter> set = emitters.get(audience);
        if (set == null || set.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : set) {
            sendExecutor.execute(() -> doSend(audience, emitter, message.event(), data));
        }
    }

    /** 只投本实例连接（不发布）。 */
    private void dispatchLocal(String audience, String eventName, Object data) {
        Set<SseEmitter> set = emitters.get(audience);
        if (set == null || set.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : set) {
            sendExecutor.execute(() -> doSend(audience, emitter, eventName, data));
        }
    }

    /**
     * 发布跨实例扇出消息（桥不存在时静默跳过 = 单实例行为）。
     *
     * <p>载荷无法被 codec 编码时记 WARN 并<strong>只投本实例</strong>：跨实例部分显式丢失，不静默降级成
     * 垃圾帧（见 {@link SseFanoutCodec#encode(Object)}）。<strong>codec 抛异常与「无法编码」同等处理</strong>：
     * 自定义 codec 的 bug 不得反向抛回业务发布线程（实时流是业务写入的旁路），只记 WARN + 完整堆栈。</p>
     */
    private void publishFanout(String eventName, @Nullable String audience, Object data) {
        SseFanoutBridge bridge = fanoutBridge;
        if (bridge == null) {
            return;
        }
        String encoded;
        try {
            encoded = codec.encode(data).orElse(null);
        } catch (Exception e) {
            // 宿主自定义 codec 可能抛任意运行时异常：本实例投递已在调用本方法前完成，
            // 跨实例部分显式丢失即可，绝不能把编码器缺陷反向抛给业务发布线程
            log.warn("[ypbin-starter] SSE 载荷编码异常，仅投本实例（跨实例部分丢失）："
                + "event={}, audience={}, codec={}, payloadType={}",
                eventName, audience, codec.id(), payloadType(data), e);
            return;
        }
        if (encoded == null) {
            log.warn("[ypbin-starter] SSE 载荷无法被 codec={} 编码，仅投本实例（跨实例部分丢失）："
                + "event={}, audience={}, payloadType={}",
                codec.id(), eventName, audience, payloadType(data));
            return;
        }
        try {
            bridge.publish(new SseFanoutMessage(eventName, audience, codec.id(), encoded));
        } catch (Exception e) {
            // 实时流是业务写入的旁路：跨实例发布失败不能反向拖垮调用方，但必须留下可排查的 WARN
            log.warn("[ypbin-starter] SSE 跨实例扇出发布失败（本实例投递不受影响）：event={}, audience={}",
                eventName, audience, e);
        }
    }

    /**
     * 载荷类型名（仅用于日志；{@code data} 允许为 {@code null}，日志语句不得自行 NPE）。
     *
     * @param data 载荷
     * @return 类型名；{@code null} 载荷返回 {@code "null"}
     */
    private static String payloadType(@Nullable Object data) {
        return data == null ? "null" : data.getClass().getName();
    }

    /**
     * 指定用户是否在线。
     *
     * @param userId 用户标识
     * @return 是否在线
     */
    public boolean isOnline(String userId) {
        Set<SseEmitter> set = emitters.get(userId);
        return set != null && !set.isEmpty();
    }

    /**
     * 在线用户数（去重）。
     *
     * @return 在线用户数
     */
    public int onlineCount() {
        return emitters.size();
    }

    /**
     * Spring 容器关闭时释放心跳调度器资源。
     */
    @Override
    public void destroy() {
        // 先停止新推送入队，再等进行中的 doSend 完成（最多 5s），保证优雅关闭
        sendExecutor.shutdown();
        try {
            if (!sendExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                sendExecutor.shutdownNow();
            }
        } catch (InterruptedException ignored) {
            sendExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        ScheduledExecutorService s = scheduler;
        if (s != null) {
            s.shutdownNow();
        }
    }

    private void doSend(String audience, SseEmitter emitter, String eventName, @Nullable Object data) {
        try {
            // SseEventBuilder.data(...) 不接受 null（NullAway 亦不允许）。null 载荷在本模块有明确语义：
            // 「事件名 + 空数据」——多实例扇出时对象载荷无法被默认 codec 承载，订阅方解码得到 null，
            // 此时应把事件名照常投出去（客户端按事件名处理），而不是整个事件都不发。
            SseEmitter.SseEventBuilder builder = SseEmitter.event().name(eventName);
            if (data != null) {
                builder.data(data);
            }
            emitter.send(builder);
        } catch (Exception e) {
            // 发送失败通常意味着客户端已断开，摘除该连接
            log.debug("[ypbin-starter] SSE 推送失败，摘除连接：audience={}, event={}", audience, eventName);
            remove(audience, emitter);
        }
    }

    // 包级可见：供测试替换 emitter 实现（如记录「收到了什么」的替身、复现发送失败）。
    // 生产路径始终返回真实的 SseEmitter；包级可见不属于公开 API（japicmp 只比对 public/protected）。
    SseEmitter newEmitter() {
        return new SseEmitter(timeoutMillis);
    }

    // 包级可见：连接完成/超时/异常回调触发；亦供并发测试直接复现竞态路径
    void remove(String audience, SseEmitter emitter) {
        Set<SseEmitter> set = emitters.get(audience);
        if (set == null) {
            return;
        }
        set.remove(emitter);
        // 该目标已无连接时移除键，避免内存泄漏。用 computeIfPresent 在 bin 锁内原子重判：
        // 若判空到删除之间有新连接并发加入（connect 复用同一 set 引用），此处 v 非空则保留，
        // 避免 remove(key, value) 仅比对引用而误删含新连接的 set，导致新连接成孤儿、收不到推送。
        if (set.isEmpty()) {
            emitters.computeIfPresent(audience, (k, v) -> v.isEmpty() ? null : v);
        }
    }

    /**
     * 当前所有在线扇出目标键（只读快照）。
     *
     * <p>方法名保留历史签名；返回的含义是「当前有连接的注册键」。默认只按 userId 建连时即在线用户集合。</p>
     *
     * @return 扇出目标键集合
     */
    public Collection<String> onlineUsers() {
        return Set.copyOf(emitters.keySet());
    }

    /**
     * 当前所有在线扇出目标键（只读快照；语义化命名的新增 API）。
     *
     * @return 扇出目标键集合
     */
    public Collection<String> onlineAudiences() {
        return Set.copyOf(emitters.keySet());
    }
}

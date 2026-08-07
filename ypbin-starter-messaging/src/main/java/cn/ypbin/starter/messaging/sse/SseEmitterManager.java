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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 连接管理器。
 *
 * <p>维护「用户 → 该用户的多个 SSE 连接」注册表，支持同一用户多端/多标签页同时在线。负责创建
 * 连接、注册完成/超时/异常时自动摘除，以及向指定用户或全体推送。连接对象为内存态，仅单实例有效；
 * 多实例扇出需在上层配合 Redis Pub/Sub 等（见文档）。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class SseEmitterManager {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterManager.class);

    /** 用户 → 其所有 SSE 连接（同一用户可多端在线） */
    private final Map<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    private final long timeoutMillis;

    /** 心跳间隔（毫秒），<=0 表示关闭心跳 */
    private final long heartbeatIntervalMillis;

    /** 心跳调度器（懒创建，daemon 线程不阻塞关闭）；所有连接共享 */
    private volatile ScheduledExecutorService scheduler;

    public SseEmitterManager(long timeoutMillis, long heartbeatIntervalSeconds) {
        this.timeoutMillis = timeoutMillis;
        this.heartbeatIntervalMillis = Math.max(0, heartbeatIntervalSeconds) * 1000L;
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
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        emitters.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(emitter);

        ScheduledFuture<?> heartbeat = startHeartbeat(userId, emitter);
        emitter.onCompletion(() -> {
            cancelHeartbeat(heartbeat);
            remove(userId, emitter);
        });
        emitter.onTimeout(() -> {
            cancelHeartbeat(heartbeat);
            remove(userId, emitter);
        });
        emitter.onError(e -> {
            cancelHeartbeat(heartbeat);
            remove(userId, emitter);
        });
        // 建连即发一条注释帧，触发浏览器 EventSource onopen，并尽早暴露断连
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            remove(userId, emitter);
        }
        return emitter;
    }

    /**
     * 启动心跳任务：每隔 {@code heartbeatIntervalMillis} 发送一条 {@code : ping} 注释帧。
     *
     * <p>发送成功说明连接存活（同时保活中间代理）；失败说明连接已死，取消自身并回收连接。
     * 心跳无法重置容器异步总超时（配了有限 {@code timeout} 时到点仍会回收，但回收已静默化）。</p>
     *
     * @param userId  用户标识
     * @param emitter 连接
     * @return 心跳任务（未启用时返回 {@code null}）
     */
    private ScheduledFuture<?> startHeartbeat(String userId, SseEmitter emitter) {
        if (heartbeatIntervalMillis <= 0) {
            return null;
        }
        ScheduledFuture<?>[] self = new ScheduledFuture<?>[1];
        ScheduledFuture<?> future = scheduler().scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                log.debug("[ypbin-starter] SSE 心跳失败，回收连接：userId={}", userId);
                if (self[0] != null) {
                    self[0].cancel(false);
                }
                remove(userId, emitter);
            }
        }, heartbeatIntervalMillis, heartbeatIntervalMillis, TimeUnit.MILLISECONDS);
        self[0] = future;
        return future;
    }

    /** 心跳调度线程数：小型固定池，避免单线程下某个心跳发送阻塞（如慢客户端）拖慢其余用户的心跳 */
    private static final int HEARTBEAT_THREADS = 2;

    /**
     * 心跳调度器（懒创建单例，daemon 线程）。
     *
     * @return 调度器
     */
    private ScheduledExecutorService scheduler() {
        ScheduledExecutorService s = scheduler;
        if (s == null) {
            synchronized (this) {
                s = scheduler;
                if (s == null) {
                    s = Executors.newScheduledThreadPool(HEARTBEAT_THREADS, r -> {
                        Thread t = new Thread(r, "ypbin-sse-heartbeat");
                        t.setDaemon(true);
                        return t;
                    });
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
    private static void cancelHeartbeat(ScheduledFuture<?> heartbeat) {
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
     * 向指定用户的所有连接推送。
     *
     * @param userId    用户标识
     * @param eventName 事件名
     * @param data      载荷
     */
    public void sendToUser(String userId, String eventName, Object data) {
        Set<SseEmitter> set = emitters.get(userId);
        if (set == null || set.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : set) {
            doSend(userId, emitter, eventName, data);
        }
    }

    /**
     * 向全体在线连接广播。
     *
     * @param eventName 事件名
     * @param data      载荷
     */
    public void broadcast(String eventName, Object data) {
        emitters.forEach((userId, set) -> set.forEach(emitter -> doSend(userId, emitter, eventName, data)));
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

    private void doSend(String userId, SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (Exception e) {
            // 发送失败通常意味着客户端已断开，摘除该连接
            log.debug("[ypbin-starter] SSE 推送失败，摘除连接：userId={}, event={}", userId, eventName);
            remove(userId, emitter);
        }
    }

    // 包级可见：连接完成/超时/异常回调触发；亦供并发测试直接复现竞态路径
    void remove(String userId, SseEmitter emitter) {
        Set<SseEmitter> set = emitters.get(userId);
        if (set == null) {
            return;
        }
        set.remove(emitter);
        // 用户已无连接时移除键，避免内存泄漏。用 computeIfPresent 在 bin 锁内原子重判：
        // 若判空到删除之间有新连接并发加入（connect 复用同一 set 引用），此处 v 非空则保留，
        // 避免 remove(key, value) 仅比对引用而误删含新连接的 set，导致新连接成孤儿、收不到推送。
        if (set.isEmpty()) {
            emitters.computeIfPresent(userId, (k, v) -> v.isEmpty() ? null : v);
        }
    }

    /**
     * 当前所有在线用户标识（只读快照）。
     *
     * @return 用户标识集合
     */
    public Collection<String> onlineUsers() {
        return Set.copyOf(emitters.keySet());
    }
}

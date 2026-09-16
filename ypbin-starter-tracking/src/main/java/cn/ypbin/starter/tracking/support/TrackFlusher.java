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
package cn.ypbin.starter.tracking.support;

import cn.ypbin.starter.tracking.core.TrackEvent;
import cn.ypbin.starter.tracking.core.TrackEventSink;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 埋点消费者：从有界队列按批取出事件并交给 {@link TrackEventSink} 落库。
 *
 * <p>关键设计点：</p>
 * <ul>
 *   <li><strong>独立平台线程</strong>，不占用业务线程，也不复用 {@code ypbin.async}（其
 *       {@code CALLER_RUNS} 拒绝策略会把反压打回业务请求线程）；</li>
 *   <li><strong>微批写入</strong>：先阻塞等一条，再非阻塞批取，兼顾时延与吞吐；</li>
 *   <li><strong>失败退避重试一次，仍失败则整批丢弃并计数</strong>——不无限重试，避免故障时拖垮进程；</li>
 *   <li><strong>关闭时尽力写完</strong>积压事件，不静默丢数据。</li>
 * </ul>
 *
 * @author wenbin
 * @since 2026-09-15
 */
public class TrackFlusher implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(TrackFlusher.class);

    /** 关闭时等待消费者退出的最长毫秒数 */
    private static final long SHUTDOWN_JOIN_TIMEOUT_MILLIS = 5000L;

    private final BoundedEventQueue queue;

    private final TrackEventSink sink;

    private final TrackCounters counters;

    private final int batchSize;

    private final long flushIntervalMillis;

    private final long retryBackoffMillis;

    private final ReentrantLock lifecycleLock = new ReentrantLock();

    private volatile boolean running;

    private @Nullable Thread worker;

    /**
     * 创建消费者。
     *
     * @param queue               有界队列
     * @param sink                事件落点
     * @param counters            计数器
     * @param batchSize           单批条数
     * @param flushIntervalMillis 空闲轮询间隔毫秒
     * @param retryBackoffMillis  失败重试前的退避毫秒
     */
    public TrackFlusher(BoundedEventQueue queue, TrackEventSink sink, TrackCounters counters,
                        int batchSize, long flushIntervalMillis, long retryBackoffMillis) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("tracking batchSize must be positive, but was " + batchSize);
        }
        if (flushIntervalMillis <= 0L) {
            // 非正数的轮询间隔会让 poll 立刻返回、消费者空转烧满一个核
            throw new IllegalArgumentException(
                "tracking flushIntervalMillis must be positive, but was " + flushIntervalMillis);
        }
        if (retryBackoffMillis < 0L) {
            throw new IllegalArgumentException(
                "tracking retryBackoffMillis must not be negative, but was " + retryBackoffMillis);
        }
        this.queue = queue;
        this.sink = sink;
        this.counters = counters;
        this.batchSize = batchSize;
        this.flushIntervalMillis = flushIntervalMillis;
        this.retryBackoffMillis = retryBackoffMillis;
    }

    /** 启动消费者线程（幂等）。 */
    public void start() {
        lifecycleLock.lock();
        try {
            if (running) {
                return;
            }
            running = true;
            worker = Thread.ofPlatform()
                .name("ypbin-tracking-flusher")
                .daemon(true)
                .start(this::loop);
            log.debug("[ypbin-starter] tracking flusher started, batchSize={}, flushIntervalMs={}.",
                batchSize, flushIntervalMillis);
        } catch (RuntimeException | Error ex) {
            running = false;
            worker = null;
            throw new RejectedExecutionException("无法启动埋点消费者线程", ex);
        } finally {
            lifecycleLock.unlock();
        }
    }

    /** 停止消费者并尽力写完积压事件。 */
    @Override
    public void close() {
        Thread current;
        lifecycleLock.lock();
        try {
            if (!running) {
                return;
            }
            running = false;
            current = worker;
            worker = null;
        } finally {
            lifecycleLock.unlock();
        }
        if (current != null) {
            current.interrupt();
            try {
                current.join(SHUTDOWN_JOIN_TIMEOUT_MILLIS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("[ypbin-starter] interrupted while waiting for tracking flusher to stop.");
            }
        }
    }

    private void loop() {
        List<TrackEvent> batch = new ArrayList<>(batchSize);
        try {
            while (running) {
                try {
                    TrackEvent first = queue.poll(flushIntervalMillis);
                    if (first == null) {
                        continue;
                    }
                    batch.add(first);
                    queue.drainTo(batch, batchSize - 1);
                    writeBatch(batch);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (RuntimeException ex) {
                    // 单批异常不允许终止消费者，否则埋点会永久失效
                    log.error("[ypbin-starter] unexpected error in tracking flusher loop.", ex);
                } finally {
                    batch.clear();
                }
            }
        } finally {
            writeRemaining();
            log.debug("[ypbin-starter] tracking flusher stopped.");
        }
    }

    private void writeBatch(List<TrackEvent> batch) {
        if (batch.isEmpty()) {
            return;
        }
        List<TrackEvent> toWrite = List.copyOf(batch);
        if (tryWrite(toWrite)) {
            counters.flushed(toWrite.size());
            return;
        }
        if (retryBackoffMillis > 0L) {
            try {
                Thread.sleep(retryBackoffMillis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        if (tryWrite(toWrite)) {
            counters.flushed(toWrite.size());
            return;
        }
        counters.flushFailed(toWrite.size());
        log.error("[ypbin-starter] tracking sink still failing after retry, {} event(s) dropped.", toWrite.size());
    }

    private boolean tryWrite(List<TrackEvent> events) {
        try {
            sink.write(events);
            return true;
        } catch (RuntimeException ex) {
            log.error("[ypbin-starter] tracking sink write failed for {} event(s).", events.size(), ex);
            return false;
        }
    }

    private void writeRemaining() {
        List<TrackEvent> remaining = new ArrayList<>(batchSize);
        while (true) {
            remaining.clear();
            if (queue.drainTo(remaining, batchSize) == 0) {
                return;
            }
            writeBatch(remaining);
        }
    }
}

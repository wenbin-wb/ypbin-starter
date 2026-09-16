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
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.Nullable;

/**
 * 有界事件队列。
 *
 * <p><strong>为什么必须有界</strong>：埋点允许丢弃，但不允许把内存吃光。队列满时按「丢新」
 * （{@code offer} 失败）处理，由调用方计数——<strong>绝不阻塞业务线程、绝不使用无界队列</strong>。</p>
 *
 * <p>刻意不使用 {@code ypbin.async} 的统一线程池：其默认拒绝策略是 {@code CALLER_RUNS}，
 * 队列满时会把压力反压回业务请求线程，与埋点「可丢」的语义正好相反。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
public class BoundedEventQueue {

    private final BlockingQueue<TrackEvent> queue;

    /**
     * 创建有界队列。
     *
     * @param capacity 容量（必须为正数）
     */
    public BoundedEventQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("tracking queue capacity must be positive, but was " + capacity);
        }
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    /**
     * 非阻塞入队。
     *
     * @param event 事件
     * @return 入队成功返回 {@code true}；队列已满返回 {@code false}（调用方应计数丢弃）
     */
    public boolean offer(TrackEvent event) {
        return queue.offer(event);
    }

    /**
     * 等待取出一条事件。
     *
     * @param timeoutMillis 最长等待毫秒数
     * @return 事件；超时无数据返回 {@code null}
     * @throws InterruptedException 线程被中断
     */
    public @Nullable TrackEvent poll(long timeoutMillis) throws InterruptedException {
        return queue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 批量取出（非阻塞）。
     *
     * @param target 目标集合
     * @param max    本次最多取出的条数
     * @return 实际取出条数
     */
    public int drainTo(List<TrackEvent> target, int max) {
        return queue.drainTo(target, max);
    }

    /**
     * 当前积压条数。
     *
     * @return 队列长度
     */
    public int size() {
        return queue.size();
    }

    /**
     * 是否为空。
     *
     * @return 空返回 {@code true}
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }
}

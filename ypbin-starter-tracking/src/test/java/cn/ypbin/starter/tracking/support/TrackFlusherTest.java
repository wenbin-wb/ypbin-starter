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

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.tracking.core.TrackEvent;
import cn.ypbin.starter.tracking.core.TrackEventSink;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/**
 * 消费者测试：微批落库、失败退避重试一次、失败不中断业务、关闭时写完积压、并发不丢事件。
 *
 * <p>用轮询等待代替 sleep 固定时长，避免在慢机器上出现偶发失败。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
class TrackFlusherTest {

    private static final long AWAIT_TIMEOUT_MILLIS = 5000L;

    private final TrackCounters counters = new TrackCounters();

    private static TrackEvent event(String id) {
        return new TrackEvent(id, "ui.page.view", Instant.parse("2026-09-15T10:00:00Z"),
            null, null, null, null, null, null, null, Map.of());
    }

    private static void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10L);
        }
        throw new AssertionError("等待条件超时（" + AWAIT_TIMEOUT_MILLIS + "ms）");
    }

    @Test
    void shouldWriteBatchesToSink() throws InterruptedException {
        List<TrackEvent> written = new CopyOnWriteArrayList<>();
        BoundedEventQueue queue = new BoundedEventQueue(16);
        TrackFlusher flusher = new TrackFlusher(queue, written::addAll, counters, 4, 20L, 0L);
        flusher.start();
        try {
            queue.offer(event("evt-1"));
            queue.offer(event("evt-2"));

            awaitUntil(() -> written.size() == 2);
            assertThat(written).extracting(TrackEvent::eventId).containsExactlyInAnyOrder("evt-1", "evt-2");
            assertThat(counters.snapshot().flushed()).isEqualTo(2L);
        } finally {
            flusher.close();
        }
    }

    @Test
    void shouldRetryOnceThenSucceed() throws InterruptedException {
        AtomicInteger attempts = new AtomicInteger();
        BoundedEventQueue queue = new BoundedEventQueue(4);
        TrackEventSink flakySink = events -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("首次写入失败（测试注入）");
            }
        };
        TrackFlusher flusher = new TrackFlusher(queue, flakySink, counters, 2, 20L, 0L);
        flusher.start();
        try {
            queue.offer(event("evt-1"));

            awaitUntil(() -> counters.snapshot().flushed() == 1L);
            assertThat(attempts.get()).isEqualTo(2);
            assertThat(counters.snapshot().flushFailed()).isZero();
        } finally {
            flusher.close();
        }
    }

    @Test
    void shouldDropBatchAfterRetryAndKeepRunning() throws InterruptedException {
        AtomicInteger attempts = new AtomicInteger();
        List<TrackEvent> written = new CopyOnWriteArrayList<>();
        BoundedEventQueue queue = new BoundedEventQueue(8);
        TrackEventSink failingSink = events -> {
            if (attempts.getAndIncrement() < 2) {
                throw new IllegalStateException("始终失败（测试注入）");
            }
            written.addAll(events);
        };
        TrackFlusher flusher = new TrackFlusher(queue, failingSink, counters, 2, 20L, 0L);
        flusher.start();
        try {
            queue.offer(event("evt-dropped"));
            awaitUntil(() -> counters.snapshot().flushFailed() == 1L);

            // 失败批次被丢弃，但消费者必须继续工作
            queue.offer(event("evt-ok"));
            awaitUntil(() -> written.size() == 1);
            assertThat(written.get(0).eventId()).isEqualTo("evt-ok");
            assertThat(counters.snapshot().flushed()).isEqualTo(1L);
        } finally {
            flusher.close();
        }
    }

    @Test
    void shouldFlushRemainingEventsOnClose() {
        List<TrackEvent> written = new CopyOnWriteArrayList<>();
        BoundedEventQueue queue = new BoundedEventQueue(16);
        TrackFlusher flusher = new TrackFlusher(queue, written::addAll, counters, 8, 60_000L, 0L);
        flusher.start();
        for (int index = 0; index < 5; index++) {
            queue.offer(event("evt-" + index));
        }
        flusher.close();

        assertThat(written).hasSize(5);
        assertThat(counters.snapshot().flushed()).isEqualTo(5L);
    }

    @Test
    void shouldNotLoseEventsUnderConcurrentProduce() throws InterruptedException {
        List<TrackEvent> written = new CopyOnWriteArrayList<>();
        BoundedEventQueue queue = new BoundedEventQueue(1024);
        TrackFlusher flusher = new TrackFlusher(queue, written::addAll, counters, 32, 10L, 0L);
        flusher.start();
        try {
            int total = 500;
            for (int index = 0; index < total; index++) {
                assertThat(queue.offer(event("evt-" + index))).isTrue();
            }
            awaitUntil(() -> written.size() == total);

            assertThat(written).extracting(TrackEvent::eventId).doesNotHaveDuplicates().hasSize(total);
            assertThat(counters.snapshot().flushed()).isEqualTo(total);
        } finally {
            flusher.close();
        }
    }

    @Test
    void shouldBeIdempotentOnRepeatedStartAndClose() {
        BoundedEventQueue queue = new BoundedEventQueue(4);
        TrackFlusher flusher = new TrackFlusher(queue, events -> { }, counters, 2, 20L, 0L);

        flusher.start();
        flusher.start();
        flusher.close();
        flusher.close();

        assertThat(queue.isEmpty()).isTrue();
    }
}

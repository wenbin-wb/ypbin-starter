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
package cn.ypbin.starter.tracking.core;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.tracking.support.BoundedEventQueue;
import cn.ypbin.starter.tracking.support.TrackCounters;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * 采集门面测试：唯一写入口的三条不变量——未登记事件码被拒、队列满即丢并计数、绝不抛异常。
 *
 * @author wenbin
 * @since 2026-09-15
 */
class TrackRecorderTest {

    private final TrackCounters counters = new TrackCounters();

    private final TrackingEventCatalog catalog = new TrackingEventCatalog(new ObjectMapper());

    private static TrackEvent event(String eventId, String eventCode) {
        return new TrackEvent(eventId, eventCode, Instant.parse("2026-09-15T10:00:00Z"),
            null, null, null, null, null, null, null, Map.of());
    }

    @Test
    void shouldEnqueueRegisteredEvent() {
        BoundedEventQueue queue = new BoundedEventQueue(4);
        TrackRecorder recorder = new TrackRecorder(queue, counters, catalog);

        recorder.record(event("evt-1", TrackingEventCodes.UI_PAGE_VIEW));

        assertThat(queue.size()).isEqualTo(1);
        assertThat(counters.snapshot().accepted()).isEqualTo(1L);
    }

    @Test
    void shouldRejectUnregisteredEventCode() {
        BoundedEventQueue queue = new BoundedEventQueue(4);
        TrackRecorder recorder = new TrackRecorder(queue, counters, catalog);

        recorder.record(List.of(
            event("evt-1", TrackingEventCodes.UI_PAGE_VIEW),
            event("evt-2", "ui.page.typo")));

        assertThat(queue.size()).isEqualTo(1);
        TrackCounters.Snapshot snapshot = counters.snapshot();
        assertThat(snapshot.accepted()).isEqualTo(1L);
        assertThat(snapshot.droppedOnQueueFull()).isZero();
        assertThat(snapshot.rejectedTotal()).isEqualTo(1L);
        assertThat(snapshot.rejectedByReason()).containsEntry("unregistered", 1L);
    }

    @Test
    void shouldCountDroppedWhenQueueIsFull() {
        BoundedEventQueue queue = new BoundedEventQueue(1);
        TrackRecorder recorder = new TrackRecorder(queue, counters, catalog);

        recorder.record(List.of(
            event("evt-1", TrackingEventCodes.UI_PAGE_VIEW),
            event("evt-2", TrackingEventCodes.UI_PAGE_VIEW),
            event("evt-3", TrackingEventCodes.UI_PAGE_VIEW)));

        TrackCounters.Snapshot snapshot = counters.snapshot();
        assertThat(snapshot.accepted()).isEqualTo(1L);
        assertThat(snapshot.droppedOnQueueFull()).isEqualTo(2L);
        assertThat(queue.size()).isEqualTo(1);
    }

    @Test
    void shouldReturnZeroForEmptyBatch() {
        TrackRecorder recorder = new TrackRecorder(new BoundedEventQueue(2), counters, catalog);

        assertThat(recorder.record(List.of())).isZero();
        assertThat(counters.snapshot().accepted()).isZero();
    }
}

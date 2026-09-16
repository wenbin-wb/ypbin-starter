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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.tracking.core.TrackEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 有界队列测试：容量、丢弃语义与批量取出。
 *
 * @author wenbin
 * @since 2026-09-15
 */
class BoundedEventQueueTest {

    private static TrackEvent event(String id) {
        return new TrackEvent(id, "ui.page.view", Instant.parse("2026-09-15T10:00:00Z"),
            null, null, null, null, null, null, null, Map.of());
    }

    @Test
    void shouldRejectNonPositiveCapacity() {
        assertThatThrownBy(() -> new BoundedEventQueue(0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("capacity");
    }

    @Test
    void shouldDropNewEventsWhenFullInsteadOfBlocking() {
        BoundedEventQueue queue = new BoundedEventQueue(1);

        assertThat(queue.offer(event("evt-1"))).isTrue();
        assertThat(queue.offer(event("evt-2"))).isFalse();
        assertThat(queue.size()).isEqualTo(1);
        assertThat(queue.isEmpty()).isFalse();
    }

    @Test
    void shouldPollAndDrain() throws InterruptedException {
        BoundedEventQueue queue = new BoundedEventQueue(4);
        queue.offer(event("evt-1"));
        queue.offer(event("evt-2"));
        queue.offer(event("evt-3"));

        assertThat(queue.poll(10L).eventId()).isEqualTo("evt-1");
        assertThat(new BoundedEventQueue(1).poll(1L)).isNull();

        List<TrackEvent> drained = new ArrayList<>();
        assertThat(queue.drainTo(drained, 10)).isEqualTo(2);
        assertThat(drained).extracting(TrackEvent::eventId).containsExactly("evt-2", "evt-3");
        assertThat(queue.isEmpty()).isTrue();
    }
}

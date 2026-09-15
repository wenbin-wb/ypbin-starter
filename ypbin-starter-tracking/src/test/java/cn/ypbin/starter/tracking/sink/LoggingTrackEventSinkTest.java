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
package cn.ypbin.starter.tracking.sink;

import static org.assertj.core.api.Assertions.assertThatCode;

import cn.ypbin.starter.tracking.core.TrackEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link LoggingTrackEventSink} 测试。
 *
 * @author wenbin
 * @since 2026-09-15
 */
class LoggingTrackEventSinkTest {

    private final LoggingTrackEventSink sink = new LoggingTrackEventSink();

    @Test
    void shouldIgnoreEmptyBatch() {
        assertThatCode(() -> sink.write(List.of())).doesNotThrowAnyException();
    }

    @Test
    void shouldAcceptEventsWithoutThrowing() {
        TrackEvent event = new TrackEvent(
            "evt-log-1", "ui.page.view", Instant.parse("2026-09-15T10:00:00Z"),
            "ypbin-admin-ui", null, null, "/system/user", null, null, null, Map.of());

        assertThatCode(() -> sink.write(List.of(event))).doesNotThrowAnyException();
    }
}

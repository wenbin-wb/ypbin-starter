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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link TrackEvent} 契约测试。
 *
 * @author wenbin
 * @since 2026-09-15
 */
class TrackEventTest {

    private static final Instant EVENT_TIME = Instant.parse("2026-09-15T10:00:00Z");

    @Test
    void shouldExposeAllFields() {
        TrackEvent event = new TrackEvent(
            "evt-1", TrackingEventCodes.UI_PAGE_VIEW, EVENT_TIME,
            "ypbin-admin-ui", "sess-1", "anon-1", "/system/user", "https://ref.example", 1200L, Boolean.TRUE,
            Map.of("routeKey", "system_user"));

        assertThat(event.eventId()).isEqualTo("evt-1");
        assertThat(event.eventCode()).isEqualTo(TrackingEventCodes.UI_PAGE_VIEW);
        assertThat(event.eventTime()).isEqualTo(EVENT_TIME);
        assertThat(event.appId()).isEqualTo("ypbin-admin-ui");
        assertThat(event.sessionId()).isEqualTo("sess-1");
        assertThat(event.anonId()).isEqualTo("anon-1");
        assertThat(event.pageUrl()).isEqualTo("/system/user");
        assertThat(event.referrer()).isEqualTo("https://ref.example");
        assertThat(event.durationMs()).isEqualTo(1200L);
        assertThat(event.success()).isTrue();
        assertThat(event.payload()).containsEntry("routeKey", "system_user");
    }

    @Test
    void shouldAllowNullableDimensionsToBeAbsent() {
        TrackEvent event = new TrackEvent(
            "evt-2", TrackingEventCodes.AUTH_USER_LOGOUT, EVENT_TIME,
            null, null, null, null, null, null, null, Map.of());

        assertThat(event.appId()).isNull();
        assertThat(event.durationMs()).isNull();
        assertThat(event.success()).isNull();
        assertThat(event.payload()).isEmpty();
    }

    @Test
    void shouldDefensivelyCopyPayload() {
        Map<String, Object> mutable = new HashMap<>();
        mutable.put("routeKey", "system_user");
        TrackEvent event = new TrackEvent(
            "evt-3", TrackingEventCodes.UI_PAGE_VIEW, EVENT_TIME,
            null, null, null, null, null, null, null, mutable);

        mutable.put("injected", "after-construction");

        assertThat(event.payload()).containsOnlyKeys("routeKey");
    }

    @Test
    void shouldRejectMissingRequiredFields() {
        assertThatThrownBy(() -> new TrackEvent(
            null, TrackingEventCodes.UI_PAGE_VIEW, EVENT_TIME,
            null, null, null, null, null, null, null, Map.of()))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("eventId");

        assertThatThrownBy(() -> new TrackEvent(
            "evt-4", null, EVENT_TIME,
            null, null, null, null, null, null, null, Map.of()))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("eventCode");

        assertThatThrownBy(() -> new TrackEvent(
            "evt-5", TrackingEventCodes.UI_PAGE_VIEW, null,
            null, null, null, null, null, null, null, Map.of()))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("eventTime");

        assertThatThrownBy(() -> new TrackEvent(
            "evt-6", TrackingEventCodes.UI_PAGE_VIEW, EVENT_TIME,
            null, null, null, null, null, null, null, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("payload");
    }

    @Test
    void shouldImplementValueSemantics() {
        TrackEvent first = new TrackEvent(
            "evt-7", TrackingEventCodes.UI_PAGE_VIEW, EVENT_TIME,
            null, null, null, null, null, null, null, Map.of());
        TrackEvent same = new TrackEvent(
            "evt-7", TrackingEventCodes.UI_PAGE_VIEW, EVENT_TIME,
            null, null, null, null, null, null, null, Map.of());
        TrackEvent other = new TrackEvent(
            "evt-8", TrackingEventCodes.UI_PAGE_VIEW, EVENT_TIME,
            null, null, null, null, null, null, null, Map.of());

        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same).isNotEqualTo(other);
        assertThat(first.toString()).contains("evt-7").contains(TrackingEventCodes.UI_PAGE_VIEW);
    }
}

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
package cn.ypbin.starter.tracking.web;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.tracking.core.TrackEvent;
import cn.ypbin.starter.tracking.core.TrackRecorder;
import cn.ypbin.starter.tracking.core.TrackingEventCatalog;
import cn.ypbin.starter.tracking.core.TrackingEventCodes;
import cn.ypbin.starter.tracking.support.BoundedEventQueue;
import cn.ypbin.starter.tracking.support.TrackCounters;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * 采集服务测试：白名单裁剪、必填校验、体积与数量上限、逐项拒绝原因。
 *
 * @author wenbin
 * @since 2026-09-15
 */
class TrackIngestServiceTest {

    private static final String VALID_TIME = "2026-09-15T10:00:00Z";

    private BoundedEventQueue queue;

    private TrackCounters counters;

    private TrackingEventCatalog catalog;

    private TrackIngestService service;

    @BeforeEach
    void setUp() {
        queue = new BoundedEventQueue(64);
        counters = new TrackCounters();
        catalog = new TrackingEventCatalog(new ObjectMapper());
        TrackRecorder recorder = new TrackRecorder(queue, counters, catalog);
        service = new TrackIngestService(recorder, counters, catalog, 3, 64, "ypbin-default");
    }

    private static TrackIngestEvent pageView(String eventId, Map<String, Object> payload) {
        return new TrackIngestEvent(eventId, TrackingEventCodes.UI_PAGE_VIEW, VALID_TIME,
            "sess-1", "anon-1", "/system/user", null, 120L, Boolean.TRUE, payload);
    }

    @Test
    void shouldAcceptValidEventAndApplyDefaults() throws InterruptedException {
        TrackIngestResp resp = service.ingest(new TrackIngestReq(null, List.of(pageView("evt-1", Map.of()))));

        assertThat(resp.received()).isEqualTo(1);
        assertThat(resp.accepted()).isEqualTo(1);
        assertThat(resp.rejected()).isZero();
        assertThat(resp.dropped()).isZero();
        assertThat(resp.reasons()).isEmpty();

        TrackEvent queued = queue.poll(10L);
        assertThat(queued).isNotNull();
        assertThat(queued.eventId()).isEqualTo("evt-1");
        assertThat(queued.appId()).isEqualTo("ypbin-default");
    }

    @Test
    void shouldPreferRequestAppIdOverDefault() throws InterruptedException {
        service.ingest(new TrackIngestReq("ypbin-admin-ui", List.of(pageView("evt-1", Map.of()))));

        TrackEvent queued = queue.poll(10L);
        assertThat(queued).isNotNull();
        assertThat(queued.appId()).isEqualTo("ypbin-admin-ui");
    }

    @Test
    void shouldRejectEventWithoutRequiredFields() {
        TrackIngestResp resp = service.ingest(new TrackIngestReq(null, List.of(
            new TrackIngestEvent(null, TrackingEventCodes.UI_PAGE_VIEW, VALID_TIME,
                null, null, null, null, null, null, null),
            new TrackIngestEvent("evt-2", null, VALID_TIME, null, null, null, null, null, null, null))));

        assertThat(resp.rejected()).isEqualTo(2);
        assertThat(resp.reasons()).containsEntry("missingRequiredField", 2);
        assertThat(queue.isEmpty()).isTrue();
    }

    @Test
    void shouldRejectUnregisteredEventCode() {
        TrackIngestResp resp = service.ingest(new TrackIngestReq(null, List.of(
            new TrackIngestEvent("evt-1", "ui.page.typo", VALID_TIME, null, null, null, null, null, null, null))));

        assertThat(resp.rejected()).isEqualTo(1);
        assertThat(resp.reasons()).containsEntry("unregistered", 1);
        assertThat(counters.snapshot().rejectedByReason()).containsEntry("unregistered", 1L);
    }

    @Test
    void shouldRejectUnparsableEventTime() {
        TrackIngestResp resp = service.ingest(new TrackIngestReq(null, List.of(
            new TrackIngestEvent("evt-1", TrackingEventCodes.UI_PAGE_VIEW, "2026-09-15 10:00:00",
                null, null, null, null, null, null, null))));

        assertThat(resp.rejected()).isEqualTo(1);
        assertThat(resp.reasons()).containsEntry("invalidEventTime", 1);
    }

    @Test
    void shouldPrunePayloadByWhitelistAndReportReasons() throws InterruptedException {
        TrackIngestResp resp = service.ingest(new TrackIngestReq(null, List.of(
            pageView("evt-1", Map.of("password", "secret", "routeKey", 42, "routeTitle", "用户管理")))));

        // 属性级问题不拒绝事件：事件被接收，问题单独统计（否则会出现 accepted=1 同时 rejected=2）
        assertThat(resp.accepted()).isEqualTo(1);
        assertThat(resp.rejected()).isZero();
        assertThat(resp.attributeIssues()).containsEntry("payloadKeyNotAllowed", 1)
            .containsEntry("payloadTypeMismatch", 1);

        TrackEvent queued = queue.poll(10L);
        assertThat(queued).isNotNull();
        assertThat(queued.payload()).containsOnlyKeys("routeTitle");
        assertThat(queued.payload()).containsEntry("routeTitle", "用户管理");
    }

    @Test
    void shouldTruncateOverlongStringProperty() throws InterruptedException {
        // 单独放宽体积上限，隔离验证「超长字符串按目录声明长度截断」这一条契约
        TrackIngestService generous = new TrackIngestService(
            new TrackRecorder(queue, counters, catalog), counters, catalog, 3, 4096, null);
        String overlong = "x".repeat(200);
        generous.ingest(new TrackIngestReq(null, List.of(pageView("evt-1", Map.of("routeKey", overlong)))));

        TrackEvent queued = queue.poll(10L);
        assertThat(queued).isNotNull();
        assertThat((String) queued.payload().get("routeKey")).hasSize(128);
    }

    @Test
    void shouldRejectPayloadExceedingByteLimit() {
        TrackIngestResp resp = service.ingest(new TrackIngestReq(null, List.of(
            pageView("evt-1", Map.of("routeKey", "y".repeat(120))))));

        assertThat(resp.rejected()).isEqualTo(1);
        assertThat(resp.reasons()).containsEntry("payloadTooLarge", 1);
        assertThat(queue.isEmpty()).isTrue();
    }

    @Test
    void shouldRejectEventsOverPerRequestLimit() {
        TrackIngestResp resp = service.ingest(new TrackIngestReq(null, List.of(
            pageView("evt-1", Map.of()), pageView("evt-2", Map.of()),
            pageView("evt-3", Map.of()), pageView("evt-4", Map.of()))));

        assertThat(resp.received()).isEqualTo(4);
        assertThat(resp.accepted()).isEqualTo(3);
        assertThat(resp.reasons()).containsEntry("overRequestLimit", 1);
        assertThat(queue.size()).isEqualTo(3);
    }

    @Test
    void shouldReportDroppedWhenQueueIsFull() {
        TrackIngestService smallService = new TrackIngestService(
            new TrackRecorder(new BoundedEventQueue(1), counters, catalog),
            counters, catalog, 3, 64, null);

        TrackIngestResp resp = smallService.ingest(new TrackIngestReq(null, List.of(
            pageView("evt-1", Map.of()), pageView("evt-2", Map.of()))));

        assertThat(resp.accepted()).isEqualTo(1);
        assertThat(resp.dropped()).isEqualTo(1);
        assertThat(resp.rejected()).isZero();
    }

    @Test
    void shouldHandleNullRequest() {
        TrackIngestResp resp = service.ingest(null);

        assertThat(resp.received()).isZero();
        assertThat(resp.accepted()).isZero();
        assertThat(resp.rejected()).isZero();
    }
}

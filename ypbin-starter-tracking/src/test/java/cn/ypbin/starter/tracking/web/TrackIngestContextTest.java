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
import cn.ypbin.starter.tracking.core.TrackRequestContext;
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
 * 采集上下文与客户端 IP 脱敏测试。
 *
 * <p>IP/UA 必须由请求线程捕获并随事件带下去——消费者线程上取不到请求，本测试即验证「带下去了」。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
class TrackIngestContextTest {

    private static final String VALID_TIME = "2026-09-15T10:00:00Z";

    private BoundedEventQueue queue;

    private TrackCounters counters;

    private TrackingEventCatalog catalog;

    @BeforeEach
    void setUp() {
        queue = new BoundedEventQueue(8);
        counters = new TrackCounters();
        catalog = new TrackingEventCatalog(new ObjectMapper());
    }

    private TrackIngestService service(boolean anonymizeIp) {
        return new TrackIngestService(new TrackRecorder(queue, counters, catalog), counters, catalog,
            10, 4096, null, anonymizeIp);
    }

    private static TrackIngestEvent event() {
        return new TrackIngestEvent("evt-1", TrackingEventCodes.UI_PAGE_VIEW, VALID_TIME,
            "sess-1", "anon-1", "/system/user", null, 120L, Boolean.TRUE, Map.of());
    }

    @Test
    void shouldCarryContextAndMaskIpv4() throws InterruptedException {
        service(true).ingest(new TrackIngestReq(null, List.of(event())),
            new TrackRequestContext("203.0.113.45", "UA/1.0", "trace-1"));

        TrackEvent event = queue.poll(10L);
        assertThat(event).isNotNull();
        // IPv4 保留 /24
        assertThat(event.clientIp()).isEqualTo("203.0.113.0");
        assertThat(event.userAgent()).isEqualTo("UA/1.0");
        assertThat(event.traceId()).isEqualTo("trace-1");
    }

    @Test
    void shouldMaskIpv6ToFirstFourHextets() throws InterruptedException {
        service(true).ingest(new TrackIngestReq(null, List.of(event())),
            new TrackRequestContext("2001:db8:1:2:3:4:5:6", null, null));

        TrackEvent event = queue.poll(10L);
        assertThat(event).isNotNull();
        assertThat(event.clientIp()).isEqualTo("2001:db8:1:2::");
    }

    @Test
    void shouldKeepIpWhenAnonymizationDisabled() throws InterruptedException {
        service(false).ingest(new TrackIngestReq(null, List.of(event())),
            new TrackRequestContext("203.0.113.45", null, null));

        TrackEvent event = queue.poll(10L);
        assertThat(event).isNotNull();
        assertThat(event.clientIp()).isEqualTo("203.0.113.45");
    }

    @Test
    void shouldTolerateEmptyContext() throws InterruptedException {
        service(true).ingest(new TrackIngestReq(null, List.of(event())), TrackRequestContext.EMPTY);

        TrackEvent event = queue.poll(10L);
        assertThat(event).isNotNull();
        assertThat(event.clientIp()).isNull();
        assertThat(event.userAgent()).isNull();
        assertThat(event.traceId()).isNull();
    }

    @Test
    void shouldNotInventMaskWhenIpIsUnrecognizable() throws InterruptedException {
        service(true).ingest(new TrackIngestReq(null, List.of(event())),
            new TrackRequestContext("not-an-ip", null, null));

        TrackEvent event = queue.poll(10L);
        assertThat(event).isNotNull();
        // 识别不出形态时原样保留：宁可留一个异常值，也不要错改成另一段网段
        assertThat(event.clientIp()).isEqualTo("not-an-ip");
    }

    @Test
    void shouldTruncateUserAgentAndTraceId() throws InterruptedException {
        service(true).ingest(new TrackIngestReq(null, List.of(event())),
            new TrackRequestContext(null, "u".repeat(600), "t".repeat(100)));

        TrackEvent event = queue.poll(10L);
        assertThat(event).isNotNull();
        assertThat(event.userAgent()).hasSize(512);
        assertThat(event.traceId()).hasSize(64);
    }
}

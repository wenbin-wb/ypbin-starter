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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * <p>IP/UA 必须由请求线程捕获并随事件带下去——消费者线程上取不到请求，本测试即验证「带下去了」。
 * IPv6 用例刻意覆盖 {@code ::} 压缩形态：只按「段数」判断的实现会在这里同时出现「漏脱敏」与
 * 「拼出非法地址」两种问题。</p>
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

    private TrackRequestContext ingestAndGetContext(boolean anonymizeIp, TrackRequestContext context)
        throws InterruptedException {
        service(anonymizeIp).ingest(new TrackIngestReq(null, List.of(event())), context);
        TrackEvent event = queue.poll(10L);
        assertThat(event).isNotNull();
        assertThat(event.context()).isNotNull();
        return event.context();
    }

    @Test
    void shouldCarryContextAndMaskIpv4() throws InterruptedException {
        TrackRequestContext context = ingestAndGetContext(true,
            new TrackRequestContext("203.0.113.45", "UA/1.0", "trace-1", 7L, 1L));

        assertThat(context.clientIp()).isEqualTo("203.0.113.0");
        assertThat(context.userAgent()).isEqualTo("UA/1.0");
        assertThat(context.traceId()).isEqualTo("trace-1");
        assertThat(context.userId()).isEqualTo(7L);
        assertThat(context.tenantId()).isEqualTo(1L);
    }

    @Test
    void shouldMaskFullyExpandedIpv6() throws InterruptedException {
        TrackRequestContext context = ingestAndGetContext(true,
            new TrackRequestContext("2001:db8:1:2:3:4:5:6", null, null, null, null));

        // /64：保留前四段
        assertThat(context.clientIp()).isEqualTo("2001:db8:1:2:0:0:0:0");
    }

    @Test
    void shouldMaskCompressedIpv6InsteadOfLeakingIt() throws InterruptedException {
        // 压缩形态：只按段数判断会把 fe80::1（3 段）整串放过 = 完全没脱敏
        assertThat(ingestAndGetContext(true,
            new TrackRequestContext("fe80::1", null, null, null, null)).clientIp())
            .isEqualTo("fe80:0:0:0:0:0:0:0");
        assertThat(ingestAndGetContext(true,
            new TrackRequestContext("2001:db8::1", null, null, null, null)).clientIp())
            .isEqualTo("2001:db8:0:0:0:0:0:0");
        assertThat(ingestAndGetContext(true,
            new TrackRequestContext("::1", null, null, null, null)).clientIp())
            .isEqualTo("0:0:0:0:0:0:0:0");
    }

    @Test
    void shouldKeepIpWhenAnonymizationDisabled() throws InterruptedException {
        assertThat(ingestAndGetContext(false,
            new TrackRequestContext("203.0.113.45", null, null, null, null)).clientIp())
            .isEqualTo("203.0.113.45");
        assertThat(ingestAndGetContext(false,
            new TrackRequestContext("2001:db8::1", null, null, null, null)).clientIp())
            .isEqualTo("2001:db8::1");
    }

    @Test
    void shouldTolerateEmptyContext() throws InterruptedException {
        TrackRequestContext context = ingestAndGetContext(true, TrackRequestContext.EMPTY);

        assertThat(context.clientIp()).isNull();
        assertThat(context.userAgent()).isNull();
        assertThat(context.traceId()).isNull();
        assertThat(context.userId()).isNull();
    }

    @Test
    void shouldNotInventMaskWhenIpIsUnrecognizable() throws InterruptedException {
        // 识别不出形态时原样保留：宁可留一个异常值，也不要错改成另一段网段
        assertThat(ingestAndGetContext(true,
            new TrackRequestContext("not-an-ip", null, null, null, null)).clientIp())
            .isEqualTo("not-an-ip");
        // IPv4 内嵌形态不在处理范围内，同样原样保留
        assertThat(ingestAndGetContext(true,
            new TrackRequestContext("::ffff:10.1.2.3", null, null, null, null)).clientIp())
            .isEqualTo("::ffff:10.1.2.3");
    }

    @Test
    void shouldThrowOnInvalidQueueCapacity() {
        assertThatThrownBy(() -> new BoundedEventQueue(0)).isInstanceOf(IllegalArgumentException.class);
    }
}

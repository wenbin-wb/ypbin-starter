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

import cn.ypbin.starter.tracking.support.TrackCounters;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * 请求体体积闸门测试：超限直接拒绝（且不进入后续链路），未超限放行。
 *
 * @author wenbin
 * @since 2026-09-15
 */
class TrackIngestSizeFilterTest {

    private static final long MAX_BYTES = 128L;

    private final TrackCounters counters = new TrackCounters();

    private final TrackIngestSizeFilter filter =
        new TrackIngestSizeFilter(new ObjectMapper(), counters, MAX_BYTES);

    @Test
    void shouldRejectOversizedRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/tracking/ingest");
        request.setContent(new byte[(int) MAX_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chained.set(true));

        assertThat(chained).isFalse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("413");
        assertThat(counters.snapshot().rejectedByReason()).containsEntry("requestTooLarge", 1L);
    }

    @Test
    void shouldPassThroughNormalRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/tracking/ingest");
        request.setContent(new byte[16]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chained.set(true));

        assertThat(chained).isTrue();
        assertThat(counters.snapshot().rejectedTotal()).isZero();
    }
}

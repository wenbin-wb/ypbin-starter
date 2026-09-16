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
import java.util.concurrent.atomic.AtomicReference;
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

    private static final int MAX_BYTES = 128;

    private final TrackCounters counters = new TrackCounters();

    private final TrackIngestSizeFilter filter =
        new TrackIngestSizeFilter(new ObjectMapper(), counters, MAX_BYTES);

    @Test
    void shouldRejectOversizedRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/tracking/ingest");
        request.setContent(new byte[MAX_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chained.set(true));

        assertThat(chained).isFalse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("413");
        assertThat(counters.snapshot().rejectedByReason()).containsEntry("requestTooLarge", 1L);
    }

    @Test
    void shouldRejectOversizedChunkedRequestWithoutContentLength() throws Exception {
        // 分块传输时长度未知（-1）：只看 Content-Length 的实现会直接放行，这里必须靠读流拦下
        // 用匿名子类把长度伪装成未知（-1），模拟 Transfer-Encoding: chunked
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/tracking/ingest") {
            @Override
            public long getContentLengthLong() {
                return -1L;
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
        request.setContent(new byte[MAX_BYTES + 64]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chained = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chained.set(true));

        assertThat(chained).isFalse();
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("413");
        assertThat(counters.snapshot().rejectedByReason()).containsEntry("requestTooLarge", 1L);
    }

    @Test
    void shouldReplayBodyToDownstreamChain() throws Exception {
        // 缓存包装必须把请求体原样传给后续链路，否则控制器会读不到 body
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/tracking/ingest");
        request.setContent("{\"appId\":\"x\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> received = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) ->
            received.set(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8)));

        assertThat(received.get()).isEqualTo("{\"appId\":\"x\"}");
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

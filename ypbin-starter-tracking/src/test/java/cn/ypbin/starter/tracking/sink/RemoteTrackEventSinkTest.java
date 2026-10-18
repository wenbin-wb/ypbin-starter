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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.tracking.core.TrackEvent;
import cn.ypbin.starter.tracking.spi.RemoteTrackIngestClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link RemoteTrackEventSink} 的上报与失败语义测试。
 *
 * <p>锁定三条既有语义（从业务仓已生产验证的实现上收，不得退化）：</p>
 * <ul>
 *     <li>成功：整批事件原样交给上报端口（不拆分、不改写）；</li>
 *     <li>空批次短路：不发起远程调用（避免无意义往返）；</li>
 *     <li>失败必须<strong>上抛</strong>：业务失败（失败 {@link R} / {@code null}）与传输异常都不得吞掉，
 *     否则采集链路不会计失败、也不会重试，事件静默消失。</li>
 * </ul>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class RemoteTrackEventSinkTest {

    private static TrackEvent event(String code) {
        return new TrackEvent("evt-" + code, code, Instant.now(), null, null, null, null, null,
            null, null, Map.of());
    }

    @Test
    @DisplayName("成功：整批事件原样交给上报端口")
    void success_delegatesWholeBatch() {
        List<TrackEvent> batch = List.of(event("page.view"), event("click.button"));
        List<List<TrackEvent>> captured = new ArrayList<>();
        RemoteTrackEventSink sink = new RemoteTrackEventSink(events -> {
            captured.add(events);
            return R.ok();
        });

        sink.write(batch);

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0)).isSameAs(batch);
    }

    @Test
    @DisplayName("空批次：短路，不发起远程调用")
    void emptyBatch_skipsRemoteCall() {
        List<List<TrackEvent>> captured = new ArrayList<>();
        RemoteTrackEventSink sink = new RemoteTrackEventSink(events -> {
            captured.add(events);
            return R.ok();
        });

        sink.write(List.of());

        assertThat(captured).isEmpty();
    }

    @Test
    @DisplayName("业务失败 R：抛 IllegalStateException，消息含业务码与条数")
    void businessFailure_throws() {
        RemoteTrackEventSink sink = new RemoteTrackEventSink(events -> R.fail(500, "system unavailable"));

        assertThatThrownBy(() -> sink.write(List.of(event("page.view"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("code=500")
            .hasMessageContaining("system unavailable");
    }

    @Test
    @DisplayName("响应为 null：按失败处理并抛异常")
    void nullResponse_treatedAsFailure() {
        RemoteTrackEventSink sink = new RemoteTrackEventSink(events -> null);

        assertThatThrownBy(() -> sink.write(List.of(event("page.view"))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("响应为空");
    }

    @Test
    @DisplayName("传输异常：原样重抛（采集链路据此计失败并退避重试一次）")
    void transportFailure_propagatesOriginalException() {
        IllegalStateException transport = new IllegalStateException("read timed out");
        RemoteTrackEventSink sink = new RemoteTrackEventSink(events -> {
            throw transport;
        });

        assertThatThrownBy(() -> sink.write(List.of(event("page.view")))).isSameAs(transport);
    }

    @Test
    @DisplayName("构造器拒绝 null 上报端口（早失败）")
    void constructor_rejectsNullClient() {
        assertThatThrownBy(() -> new RemoteTrackEventSink((RemoteTrackIngestClient) null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("RemoteTrackIngestClient");
    }
}

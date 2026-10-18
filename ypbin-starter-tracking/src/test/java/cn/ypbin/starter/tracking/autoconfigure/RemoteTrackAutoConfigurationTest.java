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
package cn.ypbin.starter.tracking.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.tracking.core.TrackEvent;
import cn.ypbin.starter.tracking.core.TrackEventSink;
import cn.ypbin.starter.tracking.sink.LoggingTrackEventSink;
import cn.ypbin.starter.tracking.sink.RemoteTrackEventSink;
import cn.ypbin.starter.tracking.spi.RemoteTrackIngestClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * {@link RemoteTrackAutoConfiguration} 的装配与顺序测试：宿主只注册
 * {@link RemoteTrackIngestClient}、不写任何顺序配置时，事件落点必须是远端实现。
 *
 * <p>与 log 侧对称：{@link TrackingAutoConfiguration#trackEventSink()} 的
 * {@code @ConditionalOnMissingBean} 只在远端实现先注册时退让。本类用
 * {@link ZzzUnorderedRemoteTrackFixture} 做反向对照，证明正向结论来自
 * {@code @AutoConfigureBefore} 而非类名字典序。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class RemoteTrackAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            TrackingAutoConfiguration.class, RemoteTrackAutoConfiguration.class))
        .withPropertyValues("ypbin.tracking.enabled=true");

    @Test
    @DisplayName("★ 存在 RemoteTrackIngestClient Bean：TrackEventSink 装配远端实现（非 LoggingTrackEventSink）")
    void withIngestClient_sinkIsRemoteImplementation() {
        RecordingIngestClient ingestClient = new RecordingIngestClient();

        runner.withBean(RemoteTrackIngestClient.class, () -> ingestClient).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(TrackEventSink.class);
            assertThat(context.getBean(TrackEventSink.class)).isInstanceOf(RemoteTrackEventSink.class);
            assertThat(context.getBean(TrackEventSink.class)).isNotInstanceOf(LoggingTrackEventSink.class);

            // 端到端接线验证：写入穿透到宿主的上报端口
            TrackEvent event = new TrackEvent("evt-1", "page.view", Instant.now(), null, null, null,
                null, null, null, null, Map.of());
            context.getBean(TrackEventSink.class).write(List.of(event));
            assertThat(ingestClient.batches).hasSize(1);
            assertThat(ingestClient.batches.get(0)).containsExactly(event);
        });
    }

    @Test
    @DisplayName("没有 RemoteTrackIngestClient Bean：仍是默认日志落点（旧行为不变）")
    void withoutIngestClient_keepsLoggingSink() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(TrackEventSink.class)).isInstanceOf(LoggingTrackEventSink.class);
        });
    }

    @Test
    @DisplayName("埋点未启用（默认关闭）：远端配置不生效")
    void disabledBySwitch() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                TrackingAutoConfiguration.class, RemoteTrackAutoConfiguration.class))
            .withBean(RemoteTrackIngestClient.class, RecordingIngestClient::new)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(TrackEventSink.class);
            });
    }

    @Test
    @DisplayName("宿主自定义 TrackEventSink 优先（可覆盖语义未被破坏）")
    void hostSinkWins() {
        TrackEventSink hostSink = events -> {
            // 宿主实现
        };
        runner.withBean(RemoteTrackIngestClient.class, RecordingIngestClient::new)
            .withBean(TrackEventSink.class, () -> hostSink)
            .run(context -> assertThat(context.getBean(TrackEventSink.class)).isSameAs(hostSink));
    }

    @Test
    @DisplayName("★ 顺序注解有效性（反向对照）：去掉 @AutoConfigureBefore 后默认落点抢先注册")
    void withoutAutoConfigureBefore_loggingSinkWins() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                TrackingAutoConfiguration.class, ZzzUnorderedRemoteTrackFixture.class))
            .withPropertyValues("ypbin.tracking.enabled=true")
            .withBean(RemoteTrackIngestClient.class, RecordingIngestClient::new)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(TrackEventSink.class))
                    .as("无顺序注解时默认落点先注册——证明生产实现的 @AutoConfigureBefore 是必需的")
                    .isInstanceOf(LoggingTrackEventSink.class);
            });
    }

    /** 记录上报批次的假客户端。 */
    static class RecordingIngestClient implements RemoteTrackIngestClient {

        private final List<List<TrackEvent>> batches = new ArrayList<>();

        @Override
        public R<Void> ingestTrackEvents(List<TrackEvent> events) {
            batches.add(events);
            return R.ok();
        }
    }
}

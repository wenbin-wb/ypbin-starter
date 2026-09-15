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

import cn.ypbin.starter.tracking.core.TrackEventSink;
import cn.ypbin.starter.tracking.sink.LoggingTrackEventSink;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 埋点自动配置的装配场景测试（默认关闭 / 开启 / 宿主覆盖 / 属性绑定）。
 *
 * @author wenbin
 * @since 2026-09-15
 */
class TrackingAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TrackingAutoConfiguration.class));

    @Test
    void shouldNotRegisterSinkByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(TrackEventSink.class);
            assertThat(context).doesNotHaveBean(TrackingProperties.class);
        });
    }

    @Test
    void shouldRegisterLoggingSinkWhenEnabled() {
        runner.withPropertyValues("ypbin.tracking.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(TrackEventSink.class);
            assertThat(context.getBean(TrackEventSink.class)).isInstanceOf(LoggingTrackEventSink.class);
        });
    }

    @Test
    void shouldBackOffWhenHostProvidesSink() {
        TrackEventSink customized = events -> {
            // 宿主自定义落点
        };
        runner.withPropertyValues("ypbin.tracking.enabled=true")
            .withBean(TrackEventSink.class, () -> customized)
            .run(context -> assertThat(context.getBean(TrackEventSink.class)).isSameAs(customized));
    }

    @Test
    void shouldBindProperties() {
        runner.withPropertyValues(
            "ypbin.tracking.enabled=true",
            "ypbin.tracking.ingest-enabled=true",
            "ypbin.tracking.path=/custom/ingest",
            "ypbin.tracking.queue-capacity=123",
            "ypbin.tracking.batch-size=50",
            "ypbin.tracking.flush-interval-ms=2000",
            "ypbin.tracking.max-events-per-request=10",
            "ypbin.tracking.max-payload-bytes=1024",
            "ypbin.tracking.max-request-bytes=2048",
            "ypbin.tracking.sample-rate=0.5",
            "ypbin.tracking.anonymize-ip=false",
            "ypbin.tracking.allowed-origins[0]=https://admin.example")
            .run(context -> {
                TrackingProperties properties = context.getBean(TrackingProperties.class);
                assertThat(properties.isEnabled()).isTrue();
                assertThat(properties.isIngestEnabled()).isTrue();
                assertThat(properties.getPath()).isEqualTo("/custom/ingest");
                assertThat(properties.getQueueCapacity()).isEqualTo(123);
                assertThat(properties.getBatchSize()).isEqualTo(50);
                assertThat(properties.getFlushIntervalMs()).isEqualTo(2000L);
                assertThat(properties.getMaxEventsPerRequest()).isEqualTo(10);
                assertThat(properties.getMaxPayloadBytes()).isEqualTo(1024);
                assertThat(properties.getMaxRequestBytes()).isEqualTo(2048);
                assertThat(properties.getSampleRate()).isEqualTo(0.5D);
                assertThat(properties.isAnonymizeIp()).isFalse();
                assertThat(properties.getAllowedOrigins()).containsExactly("https://admin.example");
            });
    }

    @Test
    void shouldShipSafeDefaults() {
        TrackingProperties properties = new TrackingProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.isIngestEnabled()).isFalse();
        assertThat(properties.getPath()).isEqualTo("/tracking/ingest");
        assertThat(properties.getQueueCapacity()).isEqualTo(10000);
        assertThat(properties.getBatchSize()).isEqualTo(200);
        assertThat(properties.getFlushIntervalMs()).isEqualTo(1000L);
        assertThat(properties.getMaxEventsPerRequest()).isEqualTo(50);
        assertThat(properties.getMaxPayloadBytes()).isEqualTo(8192);
        assertThat(properties.getMaxRequestBytes()).isEqualTo(262144);
        assertThat(properties.getSampleRate()).isEqualTo(1.0D);
        assertThat(properties.isAnonymizeIp()).isTrue();
        assertThat(properties.getAllowedOrigins()).isEqualTo(List.of());

        properties.setEnabled(true);
        properties.setIngestEnabled(true);
        properties.setPath("/p");
        properties.setQueueCapacity(1);
        properties.setBatchSize(2);
        properties.setFlushIntervalMs(3L);
        properties.setMaxEventsPerRequest(4);
        properties.setMaxPayloadBytes(5);
        properties.setMaxRequestBytes(6);
        properties.setSampleRate(0.25D);
        properties.setAnonymizeIp(false);
        properties.setAllowedOrigins(List.of("https://a.example"));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.isIngestEnabled()).isTrue();
        assertThat(properties.getPath()).isEqualTo("/p");
        assertThat(properties.getQueueCapacity()).isEqualTo(1);
        assertThat(properties.getBatchSize()).isEqualTo(2);
        assertThat(properties.getFlushIntervalMs()).isEqualTo(3L);
        assertThat(properties.getMaxEventsPerRequest()).isEqualTo(4);
        assertThat(properties.getMaxPayloadBytes()).isEqualTo(5);
        assertThat(properties.getMaxRequestBytes()).isEqualTo(6);
        assertThat(properties.getSampleRate()).isEqualTo(0.25D);
        assertThat(properties.isAnonymizeIp()).isFalse();
        assertThat(properties.getAllowedOrigins()).containsExactly("https://a.example");
    }
}

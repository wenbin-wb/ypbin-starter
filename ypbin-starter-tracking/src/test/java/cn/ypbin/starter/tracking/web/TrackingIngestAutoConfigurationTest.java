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

import cn.ypbin.starter.tracking.autoconfigure.TrackingAutoConfiguration;
import cn.ypbin.starter.tracking.autoconfigure.TrackingProperties;
import cn.ypbin.starter.tracking.core.TrackEventSink;
import cn.ypbin.starter.tracking.core.TrackRecorder;
import cn.ypbin.starter.tracking.support.BoundedEventQueue;
import cn.ypbin.starter.tracking.support.TrackCounters;
import cn.ypbin.starter.tracking.support.TrackFlusher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

/**
 * 采集端点装配场景测试：默认关闭、显式开启、宿主覆盖、路径与限额绑定。
 *
 * @author wenbin
 * @since 2026-09-15
 */
class TrackingIngestAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TrackingAutoConfiguration.class,
            TrackingIngestAutoConfiguration.class));

    @Test
    void shouldNotRegisterEndpointWhenDisabled() {
        runner.withPropertyValues("ypbin.tracking.enabled=true").run(context -> {
            assertThat(context).doesNotHaveBean(TrackIngestController.class);
            assertThat(context).doesNotHaveBean(TrackIngestService.class);
        });
    }

    @Test
    void shouldNotRegisterEndpointWhenTrackingDisabled() {
        runner.withPropertyValues("ypbin.tracking.ingest-enabled=true").run(context ->
            assertThat(context).doesNotHaveBean(TrackIngestController.class));
    }

    @Test
    void shouldRegisterEndpointWhenExplicitlyEnabled() {
        runner.withPropertyValues("ypbin.tracking.enabled=true", "ypbin.tracking.ingest-enabled=true")
            .run(context -> {
                assertThat(context).hasSingleBean(TrackIngestController.class);
                assertThat(context).hasSingleBean(TrackIngestService.class);
                assertThat(context).hasSingleBean(TrackRecorder.class);
                assertThat(context).hasSingleBean(BoundedEventQueue.class);
                assertThat(context).hasSingleBean(TrackCounters.class);
                assertThat(context).hasSingleBean(TrackFlusher.class);
                assertThat(context).hasBean("trackIngestSizeFilter");
            });
    }

    @Test
    void shouldBindEndpointPathAndLimits() {
        runner.withPropertyValues(
            "ypbin.tracking.enabled=true",
            "ypbin.tracking.ingest-enabled=true",
            "ypbin.tracking.path=/custom/tracking",
            "ypbin.tracking.max-events-per-request=7",
            "ypbin.tracking.app-id=ypbin-admin-ui")
            .run(context -> {
                assertThat(context).hasSingleBean(TrackIngestController.class);
                assertThat(context.getBean(TrackingProperties.class).getPath()).isEqualTo("/custom/tracking");
                assertThat(context.getBean(TrackingProperties.class).getAppId()).isEqualTo("ypbin-admin-ui");
                Object filter = context.getBean("trackIngestSizeFilter");
                assertThat(filter).isInstanceOf(FilterRegistrationBean.class);
                assertThat(((FilterRegistrationBean<?>) filter).getUrlPatterns())
                    .containsExactly("/custom/tracking");
            });
    }

    @Test
    void shouldBackOffWhenHostProvidesCustomSink() {
        TrackEventSink customized = events -> {
            // 宿主自定义落点
        };
        runner.withPropertyValues("ypbin.tracking.enabled=true", "ypbin.tracking.ingest-enabled=true")
            .withBean(TrackEventSink.class, () -> customized)
            .run(context -> assertThat(context.getBean(TrackEventSink.class)).isSameAs(customized));
    }
}

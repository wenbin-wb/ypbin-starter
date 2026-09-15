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

import cn.ypbin.starter.tracking.core.TrackEventSink;
import cn.ypbin.starter.tracking.sink.LoggingTrackEventSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 埋点自动配置。
 *
 * <p>默认<strong>不生效</strong>（{@code ypbin.tracking.enabled=true} 才装配）：
 * 埋点会引入一个匿名可写入口，默认开启不是合理的默认值。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = TrackingProperties.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TrackingProperties.class)
public class TrackingAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TrackingAutoConfiguration.class);

    /**
     * 默认事件落点：仅打印到应用日志。
     *
     * <p>刻意在装配期打印 WARN：默认实现不落库，若不提示会造成「配置了埋点却没有数据」的静默降级。
     * 宿主覆盖 {@link TrackEventSink} Bean 后本方法不再执行，警告随之消失。</p>
     *
     * @return 默认落点
     */
    @Bean
    @ConditionalOnMissingBean
    public TrackEventSink trackEventSink() {
        log.warn("[ypbin-starter] tracking enabled but no TrackEventSink bean found; "
            + "events will only be written to application log. Override TrackEventSink to persist them.");
        return new LoggingTrackEventSink();
    }
}

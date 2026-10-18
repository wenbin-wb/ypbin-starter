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
import cn.ypbin.starter.tracking.sink.RemoteTrackEventSink;
import cn.ypbin.starter.tracking.spi.RemoteTrackIngestClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * {@code @AutoConfigureBefore} 有效性反向对照夹具（仅测试用，不在
 * {@code AutoConfiguration.imports} 中）。
 *
 * <p>与生产实现 {@link RemoteTrackAutoConfiguration} 唯一的差别是<strong>没有</strong>
 * {@code @AutoConfigureBefore(TrackingAutoConfiguration.class)}，且类名以 {@code Zzz} 开头
 * （字典序最后）。它跑出来必须由 {@code LoggingTrackEventSink} 抢先注册——若哪天也装配出了远端实现，
 * 就说明对照组失效，正向用例的「顺序注解真的必要」这一结论不再可信。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
@AutoConfiguration
@ConditionalOnBean(RemoteTrackIngestClient.class)
class ZzzUnorderedRemoteTrackFixture {

    @Bean
    @ConditionalOnMissingBean(TrackEventSink.class)
    TrackEventSink zzzUnorderedRemoteTrackSink(RemoteTrackIngestClient ingestClient) {
        return new RemoteTrackEventSink(ingestClient);
    }
}

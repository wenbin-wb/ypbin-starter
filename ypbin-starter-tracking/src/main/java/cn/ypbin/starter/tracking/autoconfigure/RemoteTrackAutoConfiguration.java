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
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 埋点远端上报自动配置：宿主提供 {@link RemoteTrackIngestClient} 时装配远端 {@link TrackEventSink}。
 *
 * <p><strong>宿主侧只需要两件事</strong>：① 引入本模块并开启埋点（{@code ypbin.tracking.enabled=true}）；
 * ② 把自家的远程客户端适配成 {@link RemoteTrackIngestClient} Bean：</p>
 *
 * <pre>{@code
 * @Bean
 * RemoteTrackIngestClient remoteTrackIngestClient(ISystemClient systemClient) {
 *     return systemClient::ingestTrackEvents;
 * }
 * }</pre>
 *
 * <p><strong>为什么宿主不再需要写 {@code beforeName}</strong>：{@link TrackingAutoConfiguration#trackEventSink()}
 * 的 {@code @ConditionalOnMissingBean} 只在「远端实现先注册」时退让，故本类显式声明
 * {@link AutoConfigureBefore}（{@code @AutoConfigureBefore(TrackingAutoConfiguration.class)}）——
 * 装配顺序知识留在 starter 内部，宿主不必再依赖 starter 内部类名，也不会因拼错类名而静默退回
 * 「只打日志」的默认落点。</p>
 *
 * <p><strong>对宿主注册方式的唯一要求</strong>：{@link RemoteTrackIngestClient} Bean 必须由<strong>宿主应用或
 * 普通 {@code @Configuration}</strong> 注册（其 Bean 定义先于自动配置处理完成，{@code @ConditionalOnBean}
 * 才看得见）。若改由另一个自动配置类注册，该配置需声明
 * {@code @AutoConfigureBefore(RemoteTrackAutoConfiguration.class)}，否则条件恒假、静默退回
 * 「只打日志」的默认落点（PITFALL 1）。宿主<strong>不需要</strong>写任何 {@code beforeName}。</p>
 *
 * <p><strong>可覆盖语义</strong>：宿主自己提供 {@link TrackEventSink} 时本类
 * （以及默认的 {@code LoggingTrackEventSink}）都退让；不提供 {@link RemoteTrackIngestClient} 时
 * 本类整块不生效，行为与旧版本一致。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
@AutoConfiguration
@AutoConfigureBefore(TrackingAutoConfiguration.class)
@ConditionalOnBean(RemoteTrackIngestClient.class)
@ConditionalOnProperty(prefix = TrackingProperties.PREFIX, name = "enabled", havingValue = "true")
public class RemoteTrackAutoConfiguration {

    /**
     * 远端事件落点：把事件交给宿主的 {@link RemoteTrackIngestClient} 上报。
     *
     * <p>本 Bean 先于 {@link TrackingAutoConfiguration#trackEventSink()} 求值，
     * 因此后者的 {@code @ConditionalOnMissingBean} 会退让，也不会再打出
     * 「tracking sink is not configured」的 WARN。</p>
     *
     * @param ingestClient 宿主提供的上报端口
     * @return 远端事件落点
     */
    @Bean
    @ConditionalOnMissingBean(TrackEventSink.class)
    public TrackEventSink remoteTrackEventSink(RemoteTrackIngestClient ingestClient) {
        return new RemoteTrackEventSink(ingestClient);
    }
}

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

import cn.ypbin.starter.tracking.aspect.TrackedAspect;
import cn.ypbin.starter.tracking.core.TrackEventSink;
import cn.ypbin.starter.tracking.core.TrackIdentityProvider;
import cn.ypbin.starter.tracking.core.TrackRecorder;
import cn.ypbin.starter.tracking.core.TrackingEventCatalog;
import cn.ypbin.starter.tracking.sink.LoggingTrackEventSink;
import cn.ypbin.starter.tracking.support.BoundedEventQueue;
import cn.ypbin.starter.tracking.support.TrackCounters;
import cn.ypbin.starter.tracking.support.TrackFlusher;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.ObjectMapper;

/**
 * 埋点自动配置。
 *
 * <p>默认<strong>不生效</strong>（{@code ypbin.tracking.enabled=true} 才装配）：埋点会引入一个
 * 匿名可写入口，默认开启不是合理的默认值。</p>
 *
 * <p>装配出的链路是：采集门面 → 有界队列 → 独立消费者线程 → {@link TrackEventSink}。
 * 业务请求线程只承担入队，落库与重试全部在消费者线程，两端互不拖累。</p>
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

    /**
     * 事件目录注册表；资源缺失或损坏时直接失败，不做「空目录照常运行」的降级。
     *
     * @param objectMapperProvider 容器内的 Jackson 映射器
     * @return 事件目录
     */
    @Bean
    @ConditionalOnMissingBean
    public TrackingEventCatalog trackingEventCatalog(ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new TrackingEventCatalog(objectMapperProvider.getIfAvailable(ObjectMapper::new));
    }

    /**
     * 埋点链路计数器。
     *
     * @return 计数器
     */
    @Bean
    @ConditionalOnMissingBean
    public TrackCounters trackCounters() {
        return new TrackCounters();
    }

    /**
     * 登录身份提供者：缺省不提供身份维度。
     *
     * <p>用户与租户只能在**请求线程**上取到（消费者线程既没有登录会话也没有租户上下文），
     * 所以必须由宿主实现本接口，采集侧在请求线程取值并随事件带下去；不提供时事件照常采集，
     * 只是缺少这两个分组维度。</p>
     *
     * @return 身份提供者
     */
    @Bean
    @ConditionalOnMissingBean
    public TrackIdentityProvider trackIdentityProvider() {
        return TrackIdentityProvider.NONE;
    }

    /**
     * 有界事件队列。
     *
     * @param properties 配置项
     * @return 队列
     */
    @Bean
    @ConditionalOnMissingBean
    public BoundedEventQueue boundedEventQueue(TrackingProperties properties) {
        return new BoundedEventQueue(properties.getQueueCapacity());
    }

    /**
     * 消费者：独立平台线程 + 微批落库 + 失败退避重试一次。
     *
     * @param queue      有界队列
     * @param sink       事件落点
     * @param counters   计数器
     * @param properties 配置项
     * @return 消费者
     */
    @Bean(initMethod = "start", destroyMethod = "close")
    @ConditionalOnMissingBean
    public TrackFlusher trackFlusher(BoundedEventQueue queue, TrackEventSink sink, TrackCounters counters,
                                     TrackingProperties properties) {
        return new TrackFlusher(queue, sink, counters, properties.getBatchSize(),
            properties.getFlushIntervalMs(), properties.getSinkRetryBackoffMs());
    }

    /**
     * 采集门面：唯一写入口，未登记事件码在此统一拒绝。
     *
     * @param queue    有界队列
     * @param counters 计数器
     * @param catalog  事件目录
     * @return 采集门面
     */
    @Bean
    @ConditionalOnMissingBean
    public TrackRecorder trackRecorder(BoundedEventQueue queue, TrackCounters counters,
                                       TrackingEventCatalog catalog) {
        return new TrackRecorder(queue, counters, catalog);
    }

    /**
     * 后端业务事件切面。
     *
     * @param recorder   采集门面
     * @param properties 配置项
     * @return 切面
     */
    @Bean
    @ConditionalOnMissingBean
    public TrackedAspect trackedAspect(TrackRecorder recorder, TrackingProperties properties) {
        @Nullable String appId = properties.getAppId().isBlank() ? null : properties.getAppId();
        return new TrackedAspect(recorder, appId);
    }
}

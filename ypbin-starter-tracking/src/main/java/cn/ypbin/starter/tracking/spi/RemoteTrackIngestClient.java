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
package cn.ypbin.starter.tracking.spi;

import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.tracking.core.TrackEvent;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * 埋点事件「远端上报」窄端口：把一批采集完成的 {@link TrackEvent} 交给宿主自己的传输实现。
 *
 * <p><strong>为什么需要这个端口</strong>：微服务形态下通常只有 system 服务有 {@code sys_track_event}
 * 数据源。下游服务若不提供 {@code TrackEventSink}，starter 会装配只打日志的
 * {@code LoggingTrackEventSink}——事件只进应用日志、分析口径永远查不到。本端口让宿主只需一个适配 Bean
 * （通常一行方法引用）就能把事件上报到有数据源的服务，无需自己实现 {@code TrackEventSink}、
 * 更无需知道 starter 的自动配置内部类名与装配顺序。</p>
 *
 * <p><strong>为什么不放在 {@code ypbin-starter-cloud-core}</strong>：本端口被
 * {@code ypbin-starter-tracking} 的默认 {@code RemoteTrackEventSink} 直接使用；放进 L3 的 cloud-core
 * 会让 L1 的 tracking 反向依赖 L3（违反本仓分层铁律，且 {@code LayeringRulesTest} 会在字节码级别判违规），
 * 也会让只引埋点能力的宿主被迫拖入 Spring Cloud。端口本身不绑定任何传输技术栈。</p>
 *
 * <p><strong>契约</strong>：</p>
 * <ul>
 *     <li>返回值用 starter 自己的 {@link R}（{@code ypbin-starter-core}），<strong>不依赖任何业务仓结果类型</strong>；</li>
 *     <li>传输层异常必须<strong>上抛</strong>，由 {@code RemoteTrackEventSink} 记完整堆栈后重抛，
 *     再由采集链路计入失败计数并退避重试一次（不无限重试）；</li>
 *     <li>业务失败用失败 {@link R}（或 {@code null}）表达，不得把失败包装成 {@code R.ok()}。</li>
 * </ul>
 *
 * <p>调用线程：由 {@code TrackFlusher} 的消费者线程调用（与业务请求线程隔离），允许阻塞一次远程往返；
 * 请为远程调用显式配置连接与读取超时。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
@FunctionalInterface
public interface RemoteTrackIngestClient {

    /**
     * 批量上报埋点事件。
     *
     * <p>允许返回 {@code null}（部分 Feign 实现在空响应体时返回 null），调用方会按失败处理。</p>
     *
     * @param events 待上报事件（调用方保证非空）
     * @return 远端统一响应；传输异常应直接抛出，业务失败用失败 {@link R} 表达
     */
    @Nullable R<Void> ingestTrackEvents(List<TrackEvent> events);
}

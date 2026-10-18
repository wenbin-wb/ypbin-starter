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

import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.core.util.LogSanitizer;
import cn.ypbin.starter.tracking.core.TrackEvent;
import cn.ypbin.starter.tracking.core.TrackEventSink;
import cn.ypbin.starter.tracking.spi.RemoteTrackIngestClient;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 埋点事件落点（远端上报）：把采集链路产出的事件交给宿主的 {@link RemoteTrackIngestClient}
 * 上报到「有数据源的那个服务」落库。
 *
 * <p><strong>装配时机</strong>：宿主提供 {@code RemoteTrackIngestClient} Bean 时，
 * {@code RemoteTrackAutoConfiguration} 会用它替换只打日志的 {@link LoggingTrackEventSink}，
 * 且不需要宿主提供任何装配顺序配置。</p>
 *
 * <p><strong>调用线程</strong>：由 {@code TrackFlusher} 的消费者线程调用，与业务请求线程隔离，
 * 允许阻塞（一次远程往返）。事件里的 IP/UA/链路 ID/用户等维度<strong>已由上报方在请求线程上捕获</strong>
 * （见 {@code TrackRequestContext}），本类不做二次补采——消费者线程也补不到。</p>
 *
 * <p><strong>异常语义</strong>：失败必须记完整堆栈并上抛，由采集链路计入失败计数、退避后重试一次
 * （{@code TrackFlusher}）。上抛不会波及业务请求：埋点链路与业务线程本就隔离。分两种形态：</p>
 * <ul>
 *     <li><strong>传输异常</strong>：{@code log.error} 带完整堆栈后原样重抛（保留原始栈）；</li>
 *     <li><strong>业务失败</strong>（{@code R.isSuccess() == false} 或响应为 {@code null}）：没有异常对象，
 *     显式构造 {@link IllegalStateException} 以产出可定位堆栈，记 error 后抛出。</li>
 * </ul>
 *
 * @author wenbin
 * @since 2026-10-18
 */
public class RemoteTrackEventSink implements TrackEventSink {

    private static final Logger log = LoggerFactory.getLogger(RemoteTrackEventSink.class);

    private final RemoteTrackIngestClient ingestClient;

    /**
     * 创建远端上报落点。
     *
     * @param ingestClient 宿主提供的上报端口
     */
    public RemoteTrackEventSink(RemoteTrackIngestClient ingestClient) {
        this.ingestClient = Objects.requireNonNull(ingestClient, "RemoteTrackIngestClient 不能为空");
    }

    @Override
    public void write(List<TrackEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        @Nullable R<Void> result;
        try {
            result = ingestClient.ingestTrackEvents(events);
        } catch (RuntimeException ex) {
            // 传输层失败：连接/读取超时、熔断、响应解码异常。记完整堆栈后原样上抛（保留原始栈）
            log.error("[ypbin-starter] 埋点事件远端上报失败（传输异常），本批 {} 条未落库: eventCodes={}",
                events.size(), codesOf(events), ex);
            throw ex;
        }
        if (result == null || !result.isSuccess()) {
            // 业务码失败（内部凭证不匹配、埋点未启用、对端异常）没有异常对象，显式构造以产出可定位堆栈
            IllegalStateException failure = new IllegalStateException(
                "[ypbin-starter] 埋点事件远端上报失败，本批 " + events.size() + " 条未落库: "
                    + "code=" + (result == null ? "null" : result.getCode())
                    + ", message=" + (result == null ? "响应为空" : result.getMessage()));
            log.error("[ypbin-starter] 埋点事件远端上报失败（业务码），eventCodes={}", codesOf(events), failure);
            throw failure;
        }
    }

    /**
     * 事件码摘要，仅用于日志定位（不含任何事件属性，避免把 payload 写进日志）。
     *
     * <p>事件码来自客户端，属不可信输入，写日志前经 {@code LogSanitizer} 脱敏防日志注入。</p>
     *
     * @param events 本批事件
     * @return 去重后的脱敏事件码列表文本
     */
    private static String codesOf(List<TrackEvent> events) {
        return events.stream().map(TrackEvent::eventCode).distinct().map(LogSanitizer::sanitize).toList().toString();
    }
}

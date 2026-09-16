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
package cn.ypbin.starter.tracking.core;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 一条埋点事件（不可变值对象）。
 *
 * <p><strong>字段可信度分级</strong>：{@code eventId} / {@code eventCode} / {@code eventTime} 来自客户端，
 * 仅 {@code eventId} 用于去重；{@code clientIp} / {@code userAgent} / {@code traceId} 以及
 * 用户、租户等权威维度<strong>不在客户端上报范围内</strong>，由服务端在采集时刻捕获或按身份上下文补齐
 * （客户端上报的同名值一律不可信）。</p>
 *
 * <p><strong>不可丢与可丢</strong>：埋点语义允许丢弃（队列满即丢并计数），但一旦构造出本对象，
 * 其字段完整性必须成立——因此构造期即校验必填项，不做静默兜底。</p>
 *
 * @param eventId    客户端生成的事件唯一 ID（去重键，非空）
 * @param eventCode  事件码，必须已登记在 {@code docs/tracking-events.json}（非空）
 * @param eventTime  客户端事件时间（参考值，服务端另记接收时间；非空）
 * @param appId      应用标识，可空
 * @param sessionId  会话 ID，可空
 * @param anonId     匿名标识，可空
 * @param pageUrl    页面地址（已脱敏），可空
 * @param referrer   来源地址（已脱敏），可空
 * @param durationMs 耗时毫秒（停留 / 接口 / 采集），可空
 * @param success    结果是否成功，可空
 * @param payload    事件属性（已在采集入口按白名单裁剪，非空且不可变）
 * @param clientIp   客户端 IP（采集时刻由服务端捕获并按配置脱敏），可空
 * @param userAgent  客户端 User-Agent 原串（采集时刻由服务端捕获，解析留给落库侧），可空
 * @param traceId    链路 ID（沿用网关的 {@code X-Request-Id}），可空
 * @author wenbin
 * @since 2026-09-15
 */
public record TrackEvent(
    String eventId,
    String eventCode,
    Instant eventTime,
    @Nullable String appId,
    @Nullable String sessionId,
    @Nullable String anonId,
    @Nullable String pageUrl,
    @Nullable String referrer,
    @Nullable Long durationMs,
    @Nullable Boolean success,
    Map<String, Object> payload,
    @Nullable String clientIp,
    @Nullable String userAgent,
    @Nullable String traceId) {

    /**
     * 不含请求上下文的构造器：后端切面、IoT 适配器等**没有 HTTP 请求**的场景使用。
     *
     * <p>不能把「没有请求」写成空串——那会让落库里出现一堆无意义的空维度；一律留 {@code null}。</p>
     *
     * @param eventId    客户端生成的事件唯一 ID
     * @param eventCode  事件码
     * @param eventTime  事件时间
     * @param appId      应用标识
     * @param sessionId  会话 ID
     * @param anonId     匿名标识
     * @param pageUrl    页面地址
     * @param referrer   来源地址
     * @param durationMs 耗时毫秒
     * @param success    是否成功
     * @param payload    事件属性
     */
    public TrackEvent(String eventId, String eventCode, Instant eventTime, @Nullable String appId,
                      @Nullable String sessionId, @Nullable String anonId, @Nullable String pageUrl,
                      @Nullable String referrer, @Nullable Long durationMs, @Nullable Boolean success,
                      Map<String, Object> payload) {
        this(eventId, eventCode, eventTime, appId, sessionId, anonId, pageUrl, referrer,
            durationMs, success, payload, null, null, null);
    }

    /**
     * 紧凑构造器：校验必填项并把属性表复制为不可变视图。
     *
     * <p>刻意在构造期抛错而不是补默认值：缺字段的事件属于契约违例，
     * 静默补空会让错误数据进入分析口径。</p>
     */
    public TrackEvent {
        Objects.requireNonNull(eventId, "eventId 不能为空");
        Objects.requireNonNull(eventCode, "eventCode 不能为空");
        Objects.requireNonNull(eventTime, "eventTime 不能为空");
        Objects.requireNonNull(payload, "payload 不能为空（无属性时传 Map.of()）");
        payload = Map.copyOf(payload);
    }
}

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
 * 仅 {@code eventId} 用于去重；{@code userId} / {@code tenantId} / {@code ip} 等<strong>权威维度不在本对象内</strong>，
 * 由服务端在入库前按身份上下文补齐（客户端上报的同名值一律不可信）。</p>
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
    Map<String, Object> payload) {

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

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

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * 采集端点上报的单条事件。
 *
 * <p>字段全部可空：缺失或非法由服务端逐项判定并给出具体拒绝原因（{@code unregistered} /
 * {@code missingRequiredField} / …），而不是让反序列化失败成为一个无信息的 400。</p>
 *
 * <p><strong>客户端字段一律不可信</strong>：{@code eventTime} 只作参考，服务端另记接收时间；
 * 用户/租户等权威维度由宿主在落库前按身份上下文补齐，<strong>不采信本对象的任何身份字段</strong>。</p>
 *
 * @param eventId    客户端生成的事件唯一 ID（去重键）
 * @param eventCode  事件码，必须已登记在事件目录
 * @param eventTime  客户端事件时间（ISO-8601，参考值）
 * @param sessionId  会话 ID
 * @param anonId     匿名标识
 * @param pageUrl    页面地址（客户端应已脱敏）
 * @param referrer   来源地址
 * @param durationMs 耗时毫秒（停留 / 接口 / 采集）
 * @param success    结果是否成功
 * @param payload    事件属性，键必须在该事件的白名单内；为空视为无属性
 * @author wenbin
 * @since 2026-09-15
 */
public record TrackIngestEvent(
    @Nullable String eventId,
    @Nullable String eventCode,
    @Nullable String eventTime,
    @Nullable String sessionId,
    @Nullable String anonId,
    @Nullable String pageUrl,
    @Nullable String referrer,
    @Nullable Long durationMs,
    @Nullable Boolean success,
    @Nullable Map<String, Object> payload) {
}

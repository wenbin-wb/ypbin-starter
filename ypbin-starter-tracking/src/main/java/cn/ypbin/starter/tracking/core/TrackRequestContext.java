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

import org.jspecify.annotations.Nullable;

/**
 * 采集时刻的请求上下文。
 *
 * <p><strong>为什么必须在这里捕获</strong>：IP 与 User-Agent 只能从 HTTP 请求上取，而埋点的落库发生在
 * <strong>消费者线程</strong>上——那时请求早已结束、上下文不复存在。因此必须在请求线程上捕获、
 * 随事件带下去；事后再想补是补不到的。</p>
 *
 * <p>这三项都由<strong>服务端</strong>取值，不采信客户端上报：UA 取请求头、IP 取对端地址（按配置脱敏）、
 * 链路 ID 沿用网关签发的 {@code X-Request-Id}。</p>
 *
 * @param clientIp  客户端 IP（采集时刻原值；脱敏由采集服务按配置完成）
 * @param userAgent 客户端 User-Agent 原串
 * @param traceId   链路 ID
 * @author wenbin
 * @since 2026-09-15
 */
public record TrackRequestContext(
    @Nullable String clientIp,
    @Nullable String userAgent,
    @Nullable String traceId) {

    /** 空上下文：非 Servlet 环境（如后端切面、IoT 适配器）使用，表示不携带请求维度。 */
    public static final TrackRequestContext EMPTY = new TrackRequestContext(null, null, null);
}

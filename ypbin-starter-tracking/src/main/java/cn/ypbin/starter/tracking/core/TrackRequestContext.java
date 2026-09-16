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
 * <p><strong>为什么必须在这里捕获</strong>：IP / User-Agent / 登录身份都只能从 HTTP 请求（或其线程上下文）
 * 上取，而埋点的落库发生在<strong>消费者线程</strong>上——那时请求早已结束、上下文不复存在。
 * 因此必须在请求线程上捕获、随事件带下去；事后再想补是补不到的。</p>
 *
 * <p><strong>可信度</strong>：所有字段都由服务端取值，不采信客户端上报——UA 取请求头、IP 取对端地址
 * （按配置脱敏）、链路 ID 沿用网关签发的 {@code X-Request-Id}、身份取宿主提供的
 * {@link TrackIdentityProvider}。</p>
 *
 * @param clientIp  客户端 IP（采集时刻原值；是否脱敏由采集服务按配置决定）
 * @param userAgent 客户端 User-Agent 原串
 * @param traceId   链路 ID
 * @param userId    当前登录用户 ID（未登录时为空）
 * @param tenantId  当前租户 ID（无租户上下文时为空）
 * @author wenbin
 * @since 2026-09-15
 */
public record TrackRequestContext(
    @Nullable String clientIp,
    @Nullable String userAgent,
    @Nullable String traceId,
    @Nullable Long userId,
    @Nullable Long tenantId) {

    /** 空上下文：非请求场景（后端切面、IoT 适配器）使用，表示不携带任何请求维度。 */
    public static final TrackRequestContext EMPTY = new TrackRequestContext(null, null, null, null, null);

    /**
     * 返回把客户端 IP 换成给定值后的副本。
     *
     * <p>脱敏在采集服务里做（策略由配置决定），上下文自身不持有策略，故用副本而不是原地修改。</p>
     *
     * @param maskedIp 脱敏后的 IP；为空表示保持为空
     * @return 新上下文
     */
    public TrackRequestContext withClientIp(@Nullable String maskedIp) {
        return new TrackRequestContext(maskedIp, userAgent, traceId, userId, tenantId);
    }
}

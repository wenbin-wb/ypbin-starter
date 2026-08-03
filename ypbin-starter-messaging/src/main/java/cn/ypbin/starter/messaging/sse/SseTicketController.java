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
package cn.ypbin.starter.messaging.sse;

import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SSE 一次性订阅票据签发端点。
 *
 * <p>用于「先换票再订阅」流程：{@code EventSource} 原生不能携带 {@code Authorization} 头，无法直接把 header
 * 令牌带进订阅请求。前端先用<strong>带鉴权凭据的普通请求</strong>调用本端点换取一张短时一次性票据，再用
 * {@code new EventSource('/ypbin/sse/subscribe?ticket=xxx')} 建立连接。</p>
 *
 * <p>票据绑定的用户由服务端登录态解析（{@link SseUserIdResolver}），未登录直接拒绝——票据只是「已鉴权身份」
 * 的一次性、短时凭证，不接收任何前端传入的用户标识。仅当存在 {@link SseUserIdResolver} 时才注册（同订阅端点）。</p>
 *
 * @author wenbin
 * @since 2026-08-03
 */
@RestController
public class SseTicketController {

    private final SseTicketStore ticketStore;

    private final SseUserIdResolver userIdResolver;

    private final Duration ticketTtl;

    public SseTicketController(SseTicketStore ticketStore, SseUserIdResolver userIdResolver, Duration ticketTtl) {
        this.ticketStore = ticketStore;
        this.userIdResolver = userIdResolver;
        this.ticketTtl = ticketTtl;
    }

    /**
     * 签发一张一次性订阅票据。路径由配置项决定，需带登录凭据访问（该端点应处于鉴权拦截范围内）。
     *
     * @return {@code R} 包裹的 {@code {ticket, expiresIn}}，expiresIn 为有效秒数
     */
    @PostMapping(value = "${ypbin.sse.ticket-path:/ypbin/sse/ticket}")
    public R<Map<String, Object>> issue() {
        String userId = userIdResolver.resolve()
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
        String ticket = UUID.randomUUID().toString().replace("-", "");
        ticketStore.save(ticket, userId, ticketTtl);
        return R.ok(Map.of("ticket", ticket, "expiresIn", ticketTtl.toSeconds()));
    }
}

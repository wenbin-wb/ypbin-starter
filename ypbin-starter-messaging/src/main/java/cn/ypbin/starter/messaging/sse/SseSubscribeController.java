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
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 内置 SSE 订阅端点。
 *
 * <p>前端用 {@code new EventSource('/ypbin/sse/subscribe')} 建立长连接，<strong>无需也不能传 userId</strong>：
 * 订阅用户由服务端解析，未登录直接拒绝。这样前端连接参数无需被信任，从根源杜绝「凭 URL 上的 userId
 * 订阅他人推送」的越权。</p>
 *
 * <p>用户身份的两种解析方式，按 {@code ticket} 是否存在自动选择：</p>
 * <ul>
 *     <li><strong>登录态（默认，适合 Cookie/Session 鉴权）</strong>：无 {@code ticket} 时，从 {@link SseUserIdResolver}
 *     解析当前登录用户——依赖请求自带登录凭据（{@code EventSource} 会自动带 Cookie）。</li>
 *     <li><strong>一次性票据（适合 Header 令牌鉴权）</strong>：{@code EventSource} 不能带 {@code Authorization} 头，
 *     故先用带令牌的普通请求向 {@link SseTicketController} 换票，再 {@code subscribe?ticket=xxx}。票据一次性消费、
 *     短时有效，绑定的用户在换票时已由登录态确定。</li>
 * </ul>
 *
 * <p>该端点仅在存在 {@link SseUserIdResolver} Bean（如引入 security 模块）时注册。业务方可关闭内置端点
 * （{@code ypbin.sse.register-endpoint=false}），自行编写带鉴权的订阅端点并调用 {@link SseEmitterManager#connect}。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
@RestController
public class SseSubscribeController {

    private final SseEmitterManager manager;

    private final SseUserIdResolver userIdResolver;

    private final SseTicketStore ticketStore;

    /** audience 维度扩展点；为 {@code null} 时行为与接入该能力前完全一致（只按 userId 建连）。 */
    @Nullable
    private final SseAudienceResolver audienceResolver;

    public SseSubscribeController(
        SseEmitterManager manager, SseUserIdResolver userIdResolver, SseTicketStore ticketStore) {
        this(manager, userIdResolver, ticketStore, null);
    }

    /**
     * 支持 audience 维度的构造器（新增重载，既有签名与语义保持不变）。
     *
     * @param manager          连接管理器
     * @param userIdResolver   登录态用户解析
     * @param ticketStore      一次性票据存储
     * @param audienceResolver 扇出维度扩展点；{@code null} = 只按 userId（默认行为）
     */
    public SseSubscribeController(
        SseEmitterManager manager, SseUserIdResolver userIdResolver, SseTicketStore ticketStore,
        @Nullable SseAudienceResolver audienceResolver) {
        this.manager = manager;
        this.userIdResolver = userIdResolver;
        this.ticketStore = ticketStore;
        this.audienceResolver = audienceResolver;
    }

    /**
     * 建立 SSE 订阅。路径由配置项决定，通过 {@code produces} 声明为事件流。
     *
     * <p>带 {@code ticket} 则消费票据取用户，否则从登录态解析；两者都拿不到用户则抛
     * {@link GlobalErrorCode#UNAUTHORIZED}。任何情况下都不接收前端传入的 userId。</p>
     *
     * @param ticket 一次性订阅票据，可选；Header 令牌鉴权场景下由换票端点签发
     * @return SseEmitter
     */
    @GetMapping(value = "${ypbin.sse.path:/ypbin/sse/subscribe}", produces = "text/event-stream")
    public SseEmitter subscribe(@RequestParam(value = "ticket", required = false) @Nullable String ticket) {
        String userId = (ticket != null && !ticket.isEmpty())
            ? ticketStore.consume(ticket).orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED))
            : userIdResolver.resolve().orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
        // 扇出维度：无 SseAudienceResolver 时只按 userId（与接入前完全一致）；有解析器时按其返回的
        // 每个目标各建一条连接（控制器只做编排，批量建连与注册表的细节都在 SseEmitterManager）。
        // 解析器返回空集合按未登录拒绝：不建「连上了却永远收不到事件」的静默无效连接。
        Set<String> audiences = audienceResolver == null
            ? Set.of(userId)
            : audienceResolver.resolve(userId);
        if (audiences.isEmpty()) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }
        return manager.connectAll(audiences);
    }
}

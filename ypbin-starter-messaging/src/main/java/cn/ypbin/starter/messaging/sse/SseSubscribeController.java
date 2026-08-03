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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 内置 SSE 订阅端点。
 *
 * <p>前端用 {@code new EventSource('/ypbin/sse/subscribe')} 建立长连接，<strong>无需也不能传 userId</strong>：
 * 订阅用户由服务端登录态解析（{@link SseUserIdResolver}），未登录直接拒绝。这样前端连接参数无需被信任，
 * 从根源杜绝「凭 URL 上的 userId 订阅他人推送」的越权。</p>
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

    public SseSubscribeController(SseEmitterManager manager, SseUserIdResolver userIdResolver) {
        this.manager = manager;
        this.userIdResolver = userIdResolver;
    }

    /**
     * 建立 SSE 订阅。路径由配置项决定，通过 {@code produces} 声明为事件流。
     *
     * <p>用户标识取自服务端登录态，不接收任何前端传参；未登录抛 {@link GlobalErrorCode#UNAUTHORIZED}。</p>
     *
     * @return SseEmitter
     */
    @GetMapping(value = "${ypbin.sse.path:/ypbin/sse/subscribe}", produces = "text/event-stream")
    public SseEmitter subscribe() {
        String userId = userIdResolver.resolve()
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
        return manager.connect(userId);
    }
}

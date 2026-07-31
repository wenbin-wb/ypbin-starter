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

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 内置 SSE 订阅端点。
 *
 * <p>前端用 {@code new EventSource('/ypbin/sse/subscribe?userId=xxx')} 建立长连接。生产环境应结合安全
 * 模块从登录态解析 userId，而非信任前端传参——业务方可关闭本内置端点（{@code ypbin.sse.register-endpoint=false}），
 * 自行编写带鉴权的订阅端点并调用 {@link SseEmitterManager#connect}。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
@RestController
public class SseSubscribeController {

    private final SseEmitterManager manager;

    public SseSubscribeController(SseEmitterManager manager) {
        this.manager = manager;
    }

    /**
     * 建立 SSE 订阅。路径由配置项决定，通过 {@code produces} 声明为事件流。
     *
     * @param userId 用户标识
     * @return SseEmitter
     */
    @GetMapping(value = "${ypbin.sse.path:/ypbin/sse/subscribe}", produces = "text/event-stream")
    public SseEmitter subscribe(@RequestParam("userId") String userId) {
        return manager.connect(userId);
    }
}

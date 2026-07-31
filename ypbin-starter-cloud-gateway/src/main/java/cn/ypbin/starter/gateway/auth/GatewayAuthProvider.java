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
package cn.ypbin.starter.gateway.auth;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关认证扩展点。
 *
 * <p>业务方可实现本接口，在网关入口统一校验 token，并返回需要写入下游请求的可信身份头。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public interface GatewayAuthProvider {

    /**
     * 校验当前请求。
     *
     * @param exchange 当前网关请求上下文
     * @return 认证结果
     */
    Mono<GatewayAuthResult> authenticate(ServerWebExchange exchange);
}

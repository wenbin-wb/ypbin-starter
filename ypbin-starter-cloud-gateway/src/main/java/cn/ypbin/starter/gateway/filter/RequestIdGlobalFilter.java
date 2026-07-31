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
package cn.ypbin.starter.gateway.filter;

import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 请求 ID 全局过滤器。
 *
 * <p>作为调用链入口，为每个进入网关的请求确保带有 {@code X-Request-Id}：客户端已带则沿用，
 * 否则生成一个。该 ID 会随请求头透传给下游服务（配合 cloud-core 的 Feign 头透传），
 * 贯穿整条调用链，便于日志关联与问题定位。以最高优先级执行，保证后续过滤器都能拿到。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class RequestIdGlobalFilter implements GlobalFilter, Ordered {

    private final String requestIdHeader;

    public RequestIdGlobalFilter(String requestIdHeader) {
        this.requestIdHeader = requestIdHeader;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(requestIdHeader);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }
        String finalId = requestId;
        // 改写请求头带上 requestId，并回写到响应头方便客户端排查
        ServerHttpRequest mutated = exchange.getRequest().mutate()
            .header(requestIdHeader, finalId)
            .build();
        exchange.getResponse().getHeaders().set(requestIdHeader, finalId);
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

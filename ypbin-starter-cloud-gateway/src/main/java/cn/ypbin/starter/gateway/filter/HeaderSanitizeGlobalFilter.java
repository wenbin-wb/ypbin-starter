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

import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 入口身份头清洗过滤器。
 *
 * <p>客户端请求进入网关时，移除用户、租户、角色等只应由可信网关签发的身份类请求头，
 * 防止调用方伪造 {@code X-User-Id}/{@code X-Tenant-Id} 等头后被下游服务误信。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class HeaderSanitizeGlobalFilter implements GlobalFilter, Ordered {

    private final List<String> headers;

    public HeaderSanitizeGlobalFilter(List<String> headers) {
        this.headers = headers;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest mutated = exchange.getRequest().mutate()
            .headers(httpHeaders -> headers.forEach(httpHeaders::remove))
            .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}

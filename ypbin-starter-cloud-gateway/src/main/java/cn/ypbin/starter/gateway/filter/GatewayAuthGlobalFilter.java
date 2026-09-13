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

import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.gateway.auth.GatewayAuthProvider;
import cn.ypbin.starter.gateway.auth.GatewayAuthResult;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 网关统一认证过滤器。
 *
 * <p>认证逻辑由业务方通过 {@link GatewayAuthProvider} 提供，本过滤器只负责白名单匹配、失败响应
 * 与可信身份头写入。默认不注册，只有配置开启且存在 {@link GatewayAuthProvider} 时生效。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class GatewayAuthGlobalFilter implements GlobalFilter, Ordered {

    private final GatewayAuthProvider authProvider;

    private final ObjectMapper objectMapper;

    private final List<String> excludePaths;

    /** 身份头签名标记头名（为空表示不签发标记） */
    private final String trustedSourceHeader;

    /** 身份头签名标记值（为空表示不签发标记） */
    private final String trustedSourceToken;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public GatewayAuthGlobalFilter(GatewayAuthProvider authProvider, ObjectMapper objectMapper,
            List<String> excludePaths, String trustedSourceHeader, String trustedSourceToken) {
        this.authProvider = authProvider;
        this.objectMapper = objectMapper;
        this.excludePaths = excludePaths;
        this.trustedSourceHeader = trustedSourceHeader;
        this.trustedSourceToken = trustedSourceToken;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (excludePaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path))) {
            return chain.filter(exchange);
        }
        return authProvider.authenticate(exchange)
            .flatMap(result -> handleAuthResult(exchange, chain, result));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }

    private Mono<Void> handleAuthResult(ServerWebExchange exchange, GatewayFilterChain chain, GatewayAuthResult result) {
        if (!result.isAuthenticated()) {
            String message = result.getMessage() == null || result.getMessage().isBlank()
                ? GlobalErrorCode.UNAUTHORIZED.getMessage()
                : result.getMessage();
            return writeUnauthorized(exchange, message);
        }
        ServerHttpRequest mutated = exchange.getRequest().mutate()
            .headers(headers -> {
                result.getTrustedHeaders().forEach(headers::set);
                // 同时签发来源标记：下游据此判定身份头可信（未配置则不签发，保持兼容）
                if (trustedSourceHeader != null && !trustedSourceHeader.isBlank()
                    && trustedSourceToken != null && !trustedSourceToken.isBlank()) {
                    headers.set(trustedSourceHeader, trustedSourceToken);
                }
            })
            .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private Mono<Void> writeUnauthorized(ServerWebExchange exchange, String message) {
        byte[] bytes = toJsonBytes(R.fail(GlobalErrorCode.UNAUTHORIZED.getCode(), message));
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private byte[] toJsonBytes(R<Void> body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JacksonException e) {
            return "{\"code\":401,\"message\":\"登录状态已过期，请重新登录\",\"success\":false}".getBytes(StandardCharsets.UTF_8);
        }
    }
}

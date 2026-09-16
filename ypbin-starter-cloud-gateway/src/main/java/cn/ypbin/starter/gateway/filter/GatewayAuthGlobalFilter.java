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
import org.springframework.http.HttpHeaders;
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
 * <p><strong>白名单路径的身份识别</strong>：白名单路径不强制鉴权，但若请求<strong>携带令牌</strong>，
 * 仍会尝试识别身份并写入可信头（识别失败照旧匿名放行）。原因是存在「匿名可写、但同样需要用户维度」
 * 的端点（如埋点采集）：若白名单一律不写身份头，这类端点的 user_id 会恒为空。</p>
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
            return filterExcludedPath(exchange, chain);
        }
        return authProvider.authenticate(exchange)
            .flatMap(result -> handleAuthResult(exchange, chain, result));
    }

    /**
     * 白名单路径：不强制鉴权，但携带令牌时仍尝试识别身份并写入可信头。
     *
     * <p><strong>只多识别一步，绝不收紧鉴权</strong>：无令牌直接放行（匿名是白名单路径的常态，
     * 也避免为匿名流量引入任何鉴权调用）；令牌无效或识别异常同样放行，只是不写身份头。</p>
     *
     * @param exchange 当前交换
     * @param chain    过滤器链
     * @return 继续过滤链
     */
    private Mono<Void> filterExcludedPath(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!hasToken(exchange)) {
            return chain.filter(exchange);
        }
        return Mono.defer(() -> authProvider.authenticate(exchange))
            .onErrorResume(ex -> Mono.just(GatewayAuthResult.failure("身份识别异常，按匿名放行")))
            .flatMap(result -> result.isAuthenticated()
                ? chain.filter(withTrustedHeaders(exchange, result))
                : chain.filter(exchange));
    }

    /**
     * 请求是否携带令牌（与鉴权提供者的取值口径一致：Authorization 头）。
     *
     * @param exchange 当前交换
     * @return 有非空 Authorization 头时为 {@code true}
     */
    private boolean hasToken(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return authorization != null && !authorization.isBlank();
    }

    /**
     * 把认证器签发的身份头与来源标记写入下游请求（同名头以签发值为准，客户端原始值已被
     * {@code HeaderSanitizeGlobalFilter} 清理）。
     *
     * @param exchange 当前交换
     * @param result   认证结果
     * @return 已写入可信头的新交换
     */
    private ServerWebExchange withTrustedHeaders(ServerWebExchange exchange, GatewayAuthResult result) {
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
        return exchange.mutate().request(mutated).build();
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
        return chain.filter(withTrustedHeaders(exchange, result));
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

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
package cn.ypbin.starter.gateway.blocklist;

import cn.ypbin.starter.core.blocklist.AutoBanIpGuard;
import cn.ypbin.starter.core.blocklist.BlocklistMatcher;
import cn.ypbin.starter.core.blocklist.BlocklistProperties;
import cn.ypbin.starter.core.blocklist.BlocklistRequest;
import cn.ypbin.starter.core.blocklist.BlocklistRule;
import cn.ypbin.starter.core.blocklist.ClientIpResolver;
import cn.ypbin.starter.core.model.R;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * 网关访问黑名单全局过滤器。
 *
 * <p>顺序为 {@link Ordered#HIGHEST_PRECEDENCE}：命中即拒绝，不进入身份头清洗、鉴权与限流链路，
 * 既节省攻击流量的处理成本，也不让被拉黑 IP 的请求出现在登录失败日志里。</p>
 *
 * <p>除静态/动态规则外，还检查 Redis 中的自动封禁键；业务响应完成后按状态码统计违规
 * （401/403 仅登录类路径，429 不限路径）。Redis 异常由存储实现 fail-open 处理，
 * 本过滤器不因黑名单不可用而阻断全部流量。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
public class AccessBlocklistGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AccessBlocklistGlobalFilter.class);

    /** 序列化失败时的固定合法信封（与 R 字段一致），绝不拼接可配置文案。 */
    private static final byte[] FALLBACK_REJECT_BODY = ("{\"code\":403,\"message\":\"访问已被限制\","
        + "\"data\":null,\"success\":false,\"timestamp\":\"1970-01-01T00:00:00\"}")
        .getBytes(StandardCharsets.UTF_8);

    private final BlocklistProperties properties;

    private final BlocklistMatcher matcher;

    private final ReactiveBlocklistStore store;

    private final ClientIpResolver clientIpResolver;

    private final ObjectMapper objectMapper;

    /** 非公网地址跳过计数的 WARN 只打一次（反代场景每个请求都会命中） */
    private final AtomicBoolean nonPublicWarned = new AtomicBoolean();

    public AccessBlocklistGlobalFilter(BlocklistProperties properties, BlocklistMatcher matcher,
                                       ReactiveBlocklistStore store, ClientIpResolver clientIpResolver,
                                       ObjectMapper objectMapper) {
        this.properties = properties;
        this.matcher = matcher;
        this.store = store;
        this.clientIpResolver = clientIpResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (matcher.isExcludedPath(path, properties.getExcludePaths())) {
            return chain.filter(exchange);
        }
        String clientIp = resolveClientIp(exchange);
        if (matcher.isExcludedIp(clientIp, properties.getExcludeIps())) {
            return chain.filter(exchange);
        }
        BlocklistRequest request = new BlocklistRequest(clientIp,
            exchange.getRequest().getHeaders().getFirst("User-Agent") == null
                ? "" : exchange.getRequest().getHeaders().getFirst("User-Agent"),
            path);

        return store.isBanned(clientIp)
            .defaultIfEmpty(false)
            .flatMap(banned -> {
                if (banned) {
                    return reject(exchange);
                }
                return store.rules()
                    .defaultIfEmpty(List.of())
                    .flatMap(dynamicRules -> {
                        List<BlocklistRule> rules = new ArrayList<>(properties.getRules());
                        rules.addAll(dynamicRules);
                        Optional<BlocklistRule> matched = matcher.match(request, rules, Instant.now());
                        if (matched.isPresent()) {
                            if (log.isDebugEnabled()) {
                                log.debug("[ypbin-starter] gateway access blocked: ip={} path={} rule={}",
                                    clientIp, path, matched.get().getId());
                            }
                            return reject(exchange);
                        }
                        return chain.filter(exchange).then(Mono.defer(() -> recordViolation(exchange, clientIp, path)));
                    });
            });
    }

    private Mono<Void> recordViolation(ServerWebExchange exchange, String clientIp, String path) {
        BlocklistProperties.AutoBan autoBan = properties.getAutoBan();
        if (!autoBan.isEnabled()) {
            return Mono.empty();
        }
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        int statusCode = status == null ? 0 : status.value();
        if (!autoBan.getStatusCodes().contains(statusCode)) {
            return Mono.empty();
        }
        // 429 是限流真状态码，任何路径都计数；401/403 默认不在 status-codes 内，
        // 仅当宿主显式配置真 401/403 时才按 path-prefixes 限定路径。
        if (statusCode != 429 && !matcher.isExcludedPath(path, autoBan.getPathPrefixes())) {
            return Mono.empty();
        }
        // 与 servlet / 登录失败两条旁路同口径：反代未配 trusted-proxies 时刷 429 会把代理自己封掉
        if (AutoBanIpGuard.shouldSkip(clientIp, autoBan.isAllowNonPublicIp())) {
            if (nonPublicWarned.compareAndSet(false, true)) {
                log.warn("[ypbin-starter] 客户端地址 {} 不是公网单点地址，已跳过自动封禁计数；"
                    + "若网关位于反向代理之后，请配置 ypbin.security.blocklist.trusted-proxies，"
                    + "否则 429 等计数会计在代理地址上并把代理封掉。"
                    + "纯内网部署可设置 auto-ban.allow-non-public-ip=true。", clientIp);
            }
            return Mono.empty();
        }
        return store.recordViolation(clientIp, Duration.ofSeconds(autoBan.getWindowSeconds()),
                autoBan.getThreshold(), Duration.ofSeconds(autoBan.getBanSeconds()),
                "auto-ban: http " + statusCode)
            .then();
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(
                R.fail(HttpStatus.FORBIDDEN.value(), properties.getResponseMessage()));
        } catch (Exception ex) {
            log.error("[ypbin-starter] 网关黑名单拒绝响应序列化失败，使用固定合法信封", ex);
            body = FALLBACK_REJECT_BODY;
        }
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().set("Cache-Control", "no-store");
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        String remote = remoteAddress == null || remoteAddress.getAddress() == null
            ? null : remoteAddress.getAddress().getHostAddress();
        return clientIpResolver.resolve(remote,
            exchange.getRequest().getHeaders().getFirst(ClientIpResolver.HEADER_FORWARDED_FOR),
            exchange.getRequest().getHeaders().getFirst(ClientIpResolver.HEADER_REAL_IP));
    }

    @Override
    public int getOrder() {
        // 必须早于身份头清洗/鉴权/限流；properties.order 只用于 Servlet 过滤器注册顺序
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

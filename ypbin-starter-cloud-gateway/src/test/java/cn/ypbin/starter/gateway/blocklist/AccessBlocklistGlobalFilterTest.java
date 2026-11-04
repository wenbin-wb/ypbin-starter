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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.starter.core.blocklist.BlocklistMatchType;
import cn.ypbin.starter.core.blocklist.BlocklistMatcher;
import cn.ypbin.starter.core.blocklist.BlocklistProperties;
import cn.ypbin.starter.core.blocklist.BlocklistRule;
import cn.ypbin.starter.core.blocklist.ClientIpResolver;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * 网关黑名单过滤器用例。
 *
 * @author wenbin
 * @since 2026-11-04
 */
class AccessBlocklistGlobalFilterTest {

    private final BlocklistProperties properties = new BlocklistProperties();

    private final ReactiveBlocklistStore store = mock(ReactiveBlocklistStore.class);

    private final AccessBlocklistGlobalFilter filter = new AccessBlocklistGlobalFilter(properties,
        new BlocklistMatcher(), store, new ClientIpResolver(List.of()), new ObjectMapper());

    @Test
    @DisplayName("命中规则返回 403，不进入后续过滤器链")
    void blocksByRule() {
        properties.setRules(List.of(new BlocklistRule("1", BlocklistMatchType.IP, "1.2.3.4",
            null, true, "bad")));
        when(store.isBanned(anyString())).thenReturn(Mono.just(false));
        when(store.rules()).thenReturn(Mono.just(List.of()));
        MockServerWebExchange exchange = exchange("/system/user", "1.2.3.4");
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exchange.getResponse().getBodyAsString().block())
            .contains("\"code\":403")
            .contains("\"success\":false")
            .contains("访问已被限制");
        verify(chain, never()).filter(any());
    }

    @Test
    @DisplayName("放行路径不查询黑名单")
    void skipsExcludedPath() {
        MockServerWebExchange exchange = exchange("/actuator/health", "1.2.3.4");
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block();

        verify(store, never()).isBanned(anyString());
        verify(chain).filter(exchange);
    }

    @Test
    @DisplayName("宿主显式配置真 401 时，登录路径记录一次违规")
    void recordsConfiguredRealUnauthorized() {
        when(store.isBanned(anyString())).thenReturn(Mono.just(false));
        when(store.rules()).thenReturn(Mono.just(List.of()));
        when(store.recordViolation(anyString(), any(Duration.class), anyInt(), any(Duration.class), anyString()))
            .thenReturn(Mono.just(1L));
        properties.getAutoBan().setThreshold(1);
        properties.getAutoBan().setStatusCodes(List.of(401));
        MockServerWebExchange exchange = exchange("/auth/login", "1.2.3.4");
        GatewayFilterChain chain = currentExchange -> {
            currentExchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        verify(store).recordViolation(anyString(), any(Duration.class), anyInt(), any(Duration.class), anyString());
    }

    @Test
    @DisplayName("已自动封禁的 IP 直接返回 403")
    void blocksBannedIp() {
        when(store.isBanned(anyString())).thenReturn(Mono.just(true));
        MockServerWebExchange exchange = exchange("/system/user", "1.2.3.4");
        GatewayFilterChain chain = mock(GatewayFilterChain.class);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(any());
    }

    private static MockServerWebExchange exchange(String path, String ip) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path)
            .remoteAddress(new InetSocketAddress(ip, 12345))
            .header("User-Agent", "Mozilla/5.0")
            .build());
    }
}

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
package cn.ypbin.starter.web.blocklist;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.core.blocklist.BlocklistMatchType;
import cn.ypbin.starter.core.blocklist.BlocklistMatcher;
import cn.ypbin.starter.core.blocklist.BlocklistProperties;
import cn.ypbin.starter.core.blocklist.BlocklistRule;
import cn.ypbin.starter.core.blocklist.ClientIpResolver;
import cn.ypbin.starter.core.blocklist.InMemoryBlocklistStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Servlet 黑名单过滤器用例。
 *
 * @author wenbin
 * @since 2026-11-04
 */
class AccessBlocklistFilterTest {

    private final InMemoryBlocklistStore store = new InMemoryBlocklistStore();

    private final BlocklistProperties properties = new BlocklistProperties();

    private final AccessBlocklistFilter filter = new AccessBlocklistFilter(properties, new BlocklistMatcher(),
        store, new ClientIpResolver(List.of()), new ObjectMapper());

    @Test
    @DisplayName("命中 IP 规则直接返回 403，不进入业务链")
    void blocksByRule() throws Exception {
        properties.setRules(List.of(new BlocklistRule("1", BlocklistMatchType.IP, "1.2.3.4",
            null, true, "bad")));
        MockHttpServletRequest request = request("/system/user", "1.2.3.4");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString())
            .contains("\"code\":403")
            .contains("\"success\":false")
            .contains("访问已被限制");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("放行路径与回环地址不受规则影响")
    void excludesPathAndLoopback() throws Exception {
        properties.setRules(List.of(new BlocklistRule("1", BlocklistMatchType.IP, "1.2.3.4",
            null, true, "bad")));

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request("/actuator/health", "1.2.3.4"), new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();

        MockFilterChain loopbackChain = new MockFilterChain();
        filter.doFilter(request("/system/user", "127.0.0.1"), new MockHttpServletResponse(), loopbackChain);
        assertThat(loopbackChain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("宿主显式配置真 401 时，登录路径连续失败达阈值后自动封禁")
    void autoBansAfterConfiguredRealUnauthorized() throws Exception {
        properties.getAutoBan().setThreshold(2);
        properties.getAutoBan().setWindowSeconds(60);
        properties.getAutoBan().setBanSeconds(60);
        properties.getAutoBan().setStatusCodes(List.of(401));
        FilterChain unauthorized = (request, response) -> ((HttpServletResponse) response).setStatus(401);

        filter.doFilter(request("/auth/login", "1.2.3.4"), new MockHttpServletResponse(), unauthorized);
        assertThat(store.isBanned("1.2.3.4")).isFalse();
        filter.doFilter(request("/auth/login", "1.2.3.4"), new MockHttpServletResponse(), unauthorized);

        assertThat(store.isBanned("1.2.3.4")).isTrue();
    }

    @Test
    @DisplayName("普通业务路径的 401 不参与自动封禁")
    void ignoresUnauthorizedOutsideLoginPath() throws Exception {
        properties.getAutoBan().setThreshold(1);
        properties.getAutoBan().setStatusCodes(List.of(401));
        FilterChain unauthorized = (request, response) -> ((HttpServletResponse) response).setStatus(401);

        filter.doFilter(request("/system/user", "1.2.3.4"), new MockHttpServletResponse(), unauthorized);

        assertThat(store.isBanned("1.2.3.4")).isFalse();
    }

    @Test
    @DisplayName("真 429 默认计数并在达阈值后自动封禁")
    void autoBansAfterRateLimit() throws Exception {
        properties.getAutoBan().setThreshold(1);
        properties.getAutoBan().setWindowSeconds(60);
        properties.getAutoBan().setBanSeconds(60);
        FilterChain tooManyRequests = (request, response) -> ((HttpServletResponse) response).setStatus(429);

        filter.doFilter(request("/system/user", "1.2.3.4"), new MockHttpServletResponse(), tooManyRequests);

        assertThat(store.isBanned("1.2.3.4")).isTrue();
    }

    @Test
    @DisplayName("已自动封禁的 IP 直接返回 403")
    void blocksBannedIp() throws Exception {
        store.ban("1.2.3.4", Duration.ofMinutes(10), "manual");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request("/system/user", "1.2.3.4"), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    private static MockHttpServletRequest request(String path, String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr(remoteAddress);
        request.addHeader("User-Agent", "Mozilla/5.0");
        return request;
    }
}

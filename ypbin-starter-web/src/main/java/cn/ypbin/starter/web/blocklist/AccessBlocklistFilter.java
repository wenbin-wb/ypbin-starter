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

import cn.ypbin.starter.core.blocklist.BlocklistMatcher;
import cn.ypbin.starter.core.blocklist.BlocklistProperties;
import cn.ypbin.starter.core.blocklist.BlocklistRequest;
import cn.ypbin.starter.core.blocklist.BlocklistRule;
import cn.ypbin.starter.core.blocklist.BlocklistStore;
import cn.ypbin.starter.core.blocklist.ClientIpResolver;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.web.util.WebRequestUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Servlet 侧访问黑名单过滤器。
 *
 * <p>顺序早于 XSS/业务过滤器：命中规则或自动封禁时直接返回 403，不进入后续链路。请求完成后按
 * 响应状态统计违规（401/403 仅限登录路径，429 不限路径），达到阈值后由 {@link BlocklistStore}
 * 写入临时封禁。</p>
 *
 * <p><b>放行优先</b>：{@code exclude-paths}/{@code exclude-ips} 先于规则判断，避免健康检查或
 * 运维入口被一条过宽规则误伤。Redis 不可用时存储实现按 fail-open 处理，本过滤器不额外抛错。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
public class AccessBlocklistFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AccessBlocklistFilter.class);

    private final BlocklistProperties properties;

    private final BlocklistMatcher matcher;

    private final BlocklistStore store;

    private final ClientIpResolver clientIpResolver;

    private final ObjectMapper objectMapper;

    public AccessBlocklistFilter(BlocklistProperties properties, BlocklistMatcher matcher,
                                 BlocklistStore store, ClientIpResolver clientIpResolver,
                                 ObjectMapper objectMapper) {
        this.properties = properties;
        this.matcher = matcher;
        this.store = store;
        this.clientIpResolver = clientIpResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = resolvePath(request);
        if (matcher.isExcludedPath(path, properties.getExcludePaths())) {
            filterChain.doFilter(request, response);
            return;
        }
        String clientIp = clientIpResolver.resolve(request.getRemoteAddr(),
            request.getHeader(ClientIpResolver.HEADER_FORWARDED_FOR),
            request.getHeader(ClientIpResolver.HEADER_REAL_IP));
        if (matcher.isExcludedIp(clientIp, properties.getExcludeIps())) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<BlocklistRule> matched = match(clientIp, request.getHeader("User-Agent"), path);
        if (matched.isPresent() || store.isBanned(clientIp)) {
            if (log.isDebugEnabled()) {
                log.debug("[ypbin-starter] access blocked: ip={} path={} rule={}",
                    clientIp, path, matched.map(BlocklistRule::getId).orElse("auto-ban"));
            }
            reject(response);
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            recordViolationIfNeeded(clientIp, path, response.getStatus());
        }
    }

    private Optional<BlocklistRule> match(String clientIp, String userAgent, String path) {
        List<BlocklistRule> rules = new ArrayList<>(properties.getRules());
        rules.addAll(store.rules());
        return matcher.match(new BlocklistRequest(clientIp, userAgent == null ? "" : userAgent, path),
            rules, Instant.now());
    }

    private void recordViolationIfNeeded(String clientIp, String path, int status) {
        BlocklistProperties.AutoBan autoBan = properties.getAutoBan();
        if (!autoBan.isEnabled() || !autoBan.getStatusCodes().contains(status)) {
            return;
        }
        // 429 是限流真状态码，任何路径都计数；401/403 默认不在 status-codes 内，
        // 仅当宿主显式配置真 401/403 时才按 path-prefixes 限定路径，避免过期 token 误封。
        if (status != 429 && !matcher.isExcludedPath(path, autoBan.getPathPrefixes())) {
            return;
        }
        try {
            store.recordViolation(clientIp, Duration.ofSeconds(autoBan.getWindowSeconds()),
                autoBan.getThreshold(), Duration.ofSeconds(autoBan.getBanSeconds()),
                "auto-ban: http " + status);
        } catch (RuntimeException ex) {
            // 自动封禁是旁路增强，不能影响已完成的业务响应
            log.warn("[ypbin-starter] 记录访问违规失败：ip={} status={}", clientIp, status, ex);
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.resetBuffer();
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        byte[] body = objectMapper.writeValueAsBytes(R.fail(HttpServletResponse.SC_FORBIDDEN,
            properties.getResponseMessage()));
        response.getOutputStream().write(body);
    }

    private static String resolvePath(HttpServletRequest request) {
        return WebRequestUtils.pathWithoutContext(request);
    }
}

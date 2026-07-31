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
package cn.ypbin.starter.observability.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 请求 ID MDC 关联过滤器。
 *
 * <p>从请求头读取（网关签发的）{@code X-Request-Id}，无则生成，写入 SLF4J MDC，使同一请求的所有
 * 日志都带上该 ID，便于跨服务日志聚合。请求结束后清理 MDC，防止线程池复用串号。同时回写响应头。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public class RequestIdMdcFilter extends OncePerRequestFilter {

    private final String requestIdHeader;

    private final String mdcKey;

    public RequestIdMdcFilter(String requestIdHeader, String mdcKey) {
        this.requestIdHeader = requestIdHeader;
        this.mdcKey = mdcKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String requestId = request.getHeader(requestIdHeader);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(mdcKey, requestId);
        response.setHeader(requestIdHeader, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(mdcKey);
        }
    }
}

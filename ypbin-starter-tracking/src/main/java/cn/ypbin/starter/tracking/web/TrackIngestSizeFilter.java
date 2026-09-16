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
package cn.ypbin.starter.tracking.web;

import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.tracking.core.TrackRejectionReason;
import cn.ypbin.starter.tracking.support.TrackCounters;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * 采集端点的请求体体积闸门。
 *
 * <p>为什么单独做这一层：采集端点与业务接口共用同一个 Servlet 线程池，限制单请求体积是避免
 * 匿名流量挤占业务容量的第一道手段（第二道是单请求事件数上限，第三道是队列丢弃）。</p>
 *
 * <p><strong>已知边界（不假装兜住）</strong>：仅依据 {@code Content-Length} 判定，
 * 因此分块传输（长度未知，值为 -1）不受本过滤器约束——这类请求仍受单请求事件数与单事件体积上限约束，
 * 且容器自身的请求体上限始终是最后一道防线。</p>
 *
 * @author wenbin
 * @since 2026-09-15
 */
public class TrackIngestSizeFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TrackIngestSizeFilter.class);

    private final ObjectMapper objectMapper;

    private final TrackCounters counters;

    private final long maxRequestBytes;

    /**
     * 创建体积闸门。
     *
     * @param objectMapper    用于输出统一响应体
     * @param counters        计数器
     * @param maxRequestBytes 单请求体上限
     */
    public TrackIngestSizeFilter(ObjectMapper objectMapper, TrackCounters counters, long maxRequestBytes) {
        this.objectMapper = objectMapper;
        this.counters = counters;
        this.maxRequestBytes = maxRequestBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        long declaredLength = request.getContentLengthLong();
        if (declaredLength > maxRequestBytes) {
            reject(response, declaredLength);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, long declaredLength) throws IOException {
        counters.rejected(TrackRejectionReason.REQUEST_TOO_LARGE, 1);
        // 只记录数值，不记录请求内容：请求体可能含用户可控数据（避免日志注入与隐私扩散）
        log.warn("[ypbin-starter] tracking ingest rejected: declared content length {} exceeds limit {}.",
            declaredLength, maxRequestBytes);
        response.setStatus(GlobalErrorCode.SUCCESS.getCode());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getOutputStream(), R.fail(GlobalErrorCode.PAYLOAD_TOO_LARGE));
    }
}

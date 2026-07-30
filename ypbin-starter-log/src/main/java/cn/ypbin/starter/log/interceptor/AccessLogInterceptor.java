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
package cn.ypbin.starter.log.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 全量访问日志拦截器。
 *
 * <p>无需注解，自动记录所有匹配请求的 URI、方法、状态码、耗时、客户端 IP。与基于 {@code @Log}
 * 注解的操作日志互补：后者精准采集业务操作（可落库），前者是全量访问流水（打印到日志）。
 * 用请求属性记开始时间，在 afterCompletion 计算耗时。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class AccessLogInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger("ypbin.access");
    private static final String START_TIME = "ypbin.access.start";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
        Exception ex) {
        Object start = request.getAttribute(START_TIME);
        long cost = (start instanceof Long s) ? System.currentTimeMillis() - s : -1;
        log.info("[访问] {} {} status={} cost={}ms ip={}",
            request.getMethod(),
            request.getRequestURI(),
            response.getStatus(),
            cost,
            resolveIp(request));
    }

    private String resolveIp(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP"};
        for (String header : headers) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                int comma = value.indexOf(',');
                return (comma > 0) ? value.substring(0, comma).trim() : value.trim();
            }
        }
        return request.getRemoteAddr();
    }
}

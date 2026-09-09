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
package cn.ypbin.starter.web.request;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 可重复读请求过滤器。
 *
 * <p>以最高优先级把带请求体的请求（非 multipart）替换为 {@link RepeatableReadRequestWrapper}，
 * 使下游（签名校验、日志、Controller 等）都能重复读取 body。文件上传（multipart）不缓存，
 * 避免大文件占用内存。已是包装类型时跳过，防止重复包装。</p>
 *
 * <p>包装时携带可配的缓存字节上限（默认 {@link RepeatableReadRequestWrapper#DEFAULT_MAX_BODY_BYTES}
 * 10MB，见 {@code ypbin.web.repeatable-read.max-body-bytes}），超限请求在包装阶段即被拒绝读取。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class RepeatableReadRequestFilter extends OncePerRequestFilter {

    private final long maxBodyBytes;

    /** 以默认缓存上限（10MB）构造。 */
    public RepeatableReadRequestFilter() {
        this(RepeatableReadRequestWrapper.DEFAULT_MAX_BODY_BYTES);
    }

    /**
     * 以指定缓存上限构造。
     *
     * @param maxBodyBytes 允许缓存的最大字节数，超过上限的请求体拒绝读取（见包装器注释）
     */
    public RepeatableReadRequestFilter(long maxBodyBytes) {
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if (shouldWrap(request)) {
            chain.doFilter(new RepeatableReadRequestWrapper(request, maxBodyBytes),
                response);
        } else {
            chain.doFilter(request, response);
        }
    }

    private boolean shouldWrap(HttpServletRequest request) {
        if (request instanceof RepeatableReadRequestWrapper) {
            return false;
        }
        String contentType = request.getContentType();
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase();
        // 文件上传不缓存，避免大文件占用内存；其余有 body 的类型缓存
        return !lower.startsWith("multipart/");
    }
}

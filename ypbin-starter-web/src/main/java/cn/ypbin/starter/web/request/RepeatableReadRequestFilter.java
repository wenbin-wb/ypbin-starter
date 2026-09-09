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

import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * 可重复读请求过滤器。
 *
 * <p>以最高优先级把带请求体的请求（非 multipart）替换为 {@link RepeatableReadRequestWrapper}，
 * 使下游（签名校验、日志、Controller 等）都能重复读取 body。文件上传（multipart）不缓存，
 * 避免大文件占用内存。已是包装类型时跳过，防止重复包装。</p>
 *
 * <p>包装时携带可配的缓存字节上限（默认 {@link RepeatableReadRequestWrapper#DEFAULT_MAX_BODY_BYTES}
 * 10MB，见 {@code ypbin.web.repeatable-read.max-body-bytes}），超限请求在包装阶段即被拒绝：
 * 该异常发生在进入 MVC 层之前、{@code @RestControllerAdvice} 捕获不到，故此处直接以统一响应
 * 结构（HTTP 200 + {@code R.code=413}）写回，保持对外契约一致。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class RepeatableReadRequestFilter extends OncePerRequestFilter {

    private final long maxBodyBytes;
    private final ObjectMapper objectMapper;

    /** 以默认缓存上限（10MB）与默认序列化器构造。 */
    public RepeatableReadRequestFilter() {
        this(RepeatableReadRequestWrapper.DEFAULT_MAX_BODY_BYTES, new ObjectMapper());
    }

    /**
     * 以指定缓存上限构造。
     *
     * @param maxBodyBytes 允许缓存的最大字节数，超过上限的请求体拒绝读取（见包装器注释）
     */
    public RepeatableReadRequestFilter(long maxBodyBytes) {
        this(maxBodyBytes, new ObjectMapper());
    }

    /**
     * 以指定缓存上限与 JSON 序列化器构造（超限响应以此序列化统一 {@link R} 结构）。
     *
     * @param maxBodyBytes 允许缓存的最大字节数
     * @param objectMapper JSON 序列化器（建议传容器注入的实例以复用全局规则）
     */
    public RepeatableReadRequestFilter(long maxBodyBytes, ObjectMapper objectMapper) {
        this.maxBodyBytes = maxBodyBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if (shouldWrap(request)) {
            try {
                chain.doFilter(new RepeatableReadRequestWrapper(request, maxBodyBytes), response);
            } catch (BusinessException e) {
                // 仅处理包装阶段的请求体超限（413 语义）；其余异常继续上抛走容器/MVC 错误处理
                if (e.getCode() == GlobalErrorCode.PAYLOAD_TOO_LARGE.getCode()) {
                    writeFail(response, e.getMessage());
                    return;
                }
                throw e;
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    /** 以统一响应结构写回拒绝结果（HTTP 200 + 业务码，见类注释）。 */
    private void writeFail(HttpServletResponse response, String message) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
            R.fail(GlobalErrorCode.PAYLOAD_TOO_LARGE, message)));
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

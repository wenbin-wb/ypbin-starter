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
package cn.ypbin.starter.security.internal;

import cn.ypbin.starter.security.internal.InternalTokenVerifier.Verdict;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 内部端点凭证守卫 —— <b>原始 401</b> 拒绝语义（EMQX 等「只看 HTTP 状态码」的入站对端口径）。
 *
 * <p><strong>为什么必须是 Filter 且必须是原始 401</strong>：对端是 EMQX 的 HTTP 动作，其
 * {@code emqx_bridge_http_connector.erl} <b>只按 HTTP 状态码分支</b>：HTTP 200 会被判「投递成功」，
 * 于是凭证错误的报文既不重试、也不计入 {@code dropped.*}——双端都绿而数据不存在（静默丢数据）。
 * 因此本路径的认证失败必须写成真正的 {@code 401}。</p>
 *
 * <p><strong>为什么不能由 {@code HandlerInterceptor} 承担</strong>：拦截器不写响应、只能靠抛异常，
 * 而 {@code Filter} 在 {@code DispatcherServlet} 之前直接写响应、<b>完全不进入异常处理链</b>，
 * 不依赖任何 Bean 的优先级顺序（端点级 {@code @RestControllerAdvice} 需要显式
 * {@code @Order(HIGHEST_PRECEDENCE)}，而全局处理器未标 {@code @Order} ⇒「哪个胜出」不确定，
 * 是典型的静默失效形态）。</p>
 *
 * <p><strong>安全不变量</strong>：fail-closed（凭证未配置即拒绝）、常量时间比较、日志不含凭证值、
 * 响应体为固定文案不含任何输入值——全部集中在 {@link InternalTokenVerifier} 单一定义点。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
public class InternalTokenGuard extends OncePerRequestFilter {

    /** 401 响应体的固定文案（不含任何输入值） */
    private static final String RAW_401_BODY =
        "{\"code\":\"UNAUTHORIZED\",\"message\":\"" + InternalTokenVerifier.MISMATCH_MESSAGE + "\"}";

    private final InternalTokenVerifier verifier;

    /**
     * 构造过滤器。
     *
     * @param configuredToken 配置的凭证（可为 {@code null}/空白，此时 fail-closed 一律拒绝）
     * @param headerName      凭证请求头名（空白时回退默认头名）
     */
    public InternalTokenGuard(@Nullable String configuredToken, @Nullable String headerName) {
        this.verifier = new InternalTokenVerifier(configuredToken, headerName);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {
        Verdict verdict = verifier.verify(request);
        if (verdict != Verdict.MATCH) {
            writeRawUnauthorized(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 写原始 401 响应（最小 JSON 体，不含任何输入值）。
     *
     * @param response 响应
     * @throws IOException 写响应失败
     */
    private void writeRawUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(RAW_401_BODY);
    }
}

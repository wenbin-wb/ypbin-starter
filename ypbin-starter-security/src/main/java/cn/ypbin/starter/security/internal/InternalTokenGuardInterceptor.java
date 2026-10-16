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

import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.security.internal.InternalTokenVerifier.Verdict;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 内部端点凭证守卫 —— <b>统一响应信封</b>拒绝语义（浏览器/网关接口口径）。
 *
 * <p><strong>为什么 ENVELOPE 必须是 {@code HandlerInterceptor} 而不是 {@code Filter}</strong>：
 * 信封语义靠"抛 {@link BusinessException} + 全局异常处理器转 HTTP 200 + {@code R.code=401}"实现，
 * 而 {@code @RestControllerAdvice} <b>捕获不到 Filter 抛出的异常</b>（Filter 在 DispatcherServlet
 * 之外），异常会冒泡到容器变成 500 / 错误页。因此两种语义必须用两种组件承载——这正是既有业务实现
 * 里"1 个拦截器 + 1 个过滤器"的原因，本组件原样保留该结构（不是重复，是语义必需）。</p>
 *
 * <p><strong>与业务实现的差异（仅一处，且是安全性增强）</strong>：既有实现直接注入
 * {@code InternalProperties}；本组件改为持有凭证字符串，免去对业务 {@code ypbin.internal.token}
 * 配置键的耦合（上收后配置前缀为 {@link InternalTokenProperties#PREFIX}）。</p>
 *
 * <p><strong>安全不变量</strong>：fail-closed（凭证未配置即拒绝，抛 {@code UNAUTHORIZED}）、
 * 常量时间比较、日志不含凭证值、异常 message 为固定文案不含任何输入值——全部集中在
 * {@link InternalTokenVerifier} 单一定义点。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
public class InternalTokenGuardInterceptor implements HandlerInterceptor {

    private final InternalTokenVerifier verifier;

    /**
     * 构造守卫。
     *
     * @param configuredToken 配置的凭证（可为 {@code null}/空白，此时 fail-closed 一律拒绝）
     * @param headerName      凭证请求头名（空白时回退默认头名）
     */
    public InternalTokenGuardInterceptor(@Nullable String configuredToken, @Nullable String headerName) {
        this.verifier = new InternalTokenVerifier(configuredToken, headerName);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Verdict verdict = verifier.verify(request);
        if (verdict != Verdict.MATCH) {
            // 由全局异常处理器转 HTTP 200 + R.code=401（浏览器/网关接口的既有统一约定）
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED,
                InternalTokenVerifier.messageOf(verdict));
        }
        return true;
    }
}

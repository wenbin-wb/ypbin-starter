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
package cn.ypbin.starter.cloud.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign 请求头透传拦截器。
 *
 * <p>微服务调用链上，把当前请求（网关/上游进来的）里的认证、链路追踪等请求头透传给
 * 下游 Feign 调用，保证鉴权与链路信息不丢。仅透传配置白名单内的头，避免误传
 * {@code Content-Length}/{@code Host} 等导致下游请求异常。用户、租户等身份头应由可信网关
 * 清洗/签发后再显式加入白名单。</p>
 *
 * <p>无 Web 请求上下文（如异步线程、定时任务发起的 Feign 调用）时安全跳过——那种场景应配合
 * core 的 {@code ContextPropagator} 做上下文透传后再调用。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class FeignHeaderInterceptor implements RequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(FeignHeaderInterceptor.class);

    private final List<String> propagateHeaders;

    /** 规范化后的身份头名单 */
    private final Set<String> identityHeaders;

    /** 可信来源标记头名（规范化），为空表示不校验来源 */
    private final String trustedSourceHeader;

    /** 可信来源标记期望值，为空表示不校验来源 */
    private final String trustedSourceToken;

    public FeignHeaderInterceptor(List<String> propagateHeaders, List<String> identityHeaders,
            String trustedSourceHeader, String trustedSourceToken) {
        this(propagateHeaders, identityHeaders, trustedSourceHeader, trustedSourceToken, false);
    }

    /**
     * 完整构造。
     *
     * @param propagateHeaders     需透传的请求头白名单
     * @param identityHeaders      需来源可信才透传的身份头
     * @param trustedSourceHeader  可信来源标记头名
     * @param trustedSourceToken   可信来源标记期望值（为空表示不校验来源）
     * @param requireTrustedSource 是否要求必须配置标记（true 时未配置直接启动失败）
     */
    public FeignHeaderInterceptor(List<String> propagateHeaders, List<String> identityHeaders,
            String trustedSourceHeader, String trustedSourceToken, boolean requireTrustedSource) {
        this.propagateHeaders = propagateHeaders;
        this.identityHeaders = (identityHeaders == null ? List.<String>of() : identityHeaders).stream()
            .map(FeignHeaderInterceptor::normalizeHeaderName)
            .collect(Collectors.toUnmodifiableSet());
        this.trustedSourceHeader = trustedSourceHeader;
        this.trustedSourceToken = trustedSourceToken;
        if (!hasText(this.trustedSourceToken)) {
            String message = "[ypbin-starter] 未配置 ypbin.cloud.feign.trusted-source-token，"
                + "身份头将不做来源校验直接透传；若服务可被外部直连，请配置该值并在网关签发 "
                + this.trustedSourceHeader + " 标记，防止伪造身份经 Feign 放大越权";
            if (requireTrustedSource) {
                throw new IllegalStateException(message + "（已开启 "
                    + "ypbin.cloud.feign.require-trusted-source，拒绝以不安全配置启动）");
            }
            log.warn(message);
        }
    }

    @Override
    public void apply(RequestTemplate template) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return;
        }
        boolean identityTrusted = isIdentitySourceTrusted(request);
        Set<String> existingHeaders = template.headers().keySet().stream()
            .map(FeignHeaderInterceptor::normalizeHeaderName)
            .collect(Collectors.toSet());
        for (String name : propagateHeaders) {
            String normalized = normalizeHeaderName(name);
            // 下游已显式设置该头则不覆盖（HTTP header 名大小写不敏感）
            if (existingHeaders.contains(normalized)) {
                continue;
            }
            // 身份头仅在来源可信时透传，避免不可信入站请求伪造身份后被二次转发
            if (!identityTrusted && identityHeaders.contains(normalized)) {
                continue;
            }
            String value = request.getHeader(name);
            if (value != null && !value.isBlank()) {
                template.header(name, value);
                existingHeaders.add(normalized);
            }
        }
    }

    /**
     * 判断入站请求是否来自可信来源。
     *
     * @param request 当前请求
     * @return 未配置校验标记时恒为 true（保持兼容）；否则要求标记头与配置值一致
     */
    private boolean isIdentitySourceTrusted(HttpServletRequest request) {
        if (!hasText(trustedSourceToken) || !hasText(trustedSourceHeader)) {
            return true;
        }
        String actual = request.getHeader(trustedSourceHeader);
        return hasText(actual) && trustedSourceToken.equals(actual.trim());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private HttpServletRequest currentRequest() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                return attrs.getRequest();
            }
        } catch (Exception ignored) {
            // 无 Web 上下文，安全跳过
        }
        return null;
    }

    private static String normalizeHeaderName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}

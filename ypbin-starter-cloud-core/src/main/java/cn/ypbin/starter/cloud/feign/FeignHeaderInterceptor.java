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
import org.jspecify.annotations.Nullable;
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
 * <p><strong>内部调用凭证的携带</strong>（与 {@code ypbin-starter-security} 的
 * 内部端点守卫衔接）：配置了 {@code ypbin.cloud.feign.internal-token} 时，本拦截器为<b>每个</b>
 * Feign 请求写入 {@code ypbin.cloud.feign.internal-token-header}（默认 {@code X-Internal-Token}）头。
 * 与白名单透传不同，这一步<b>不依赖入站请求上下文</b>——同一应用内由定时任务、异步线程发起的服务间
 * 调用同样会带上凭证，否则下游一旦开启守卫（fail-closed）就会 401。模板已显式设置该头时不覆盖
 * （与既有头处理同口径）。</p>
 *
 * <p><strong>装配前提（重要，勿误读）</strong>：本拦截器 Bean 由
 * {@code CloudFeignAutoConfiguration.FeignHeaderConfiguration} 注册，装配条件仅为
 * 「classpath 存在 Servlet API」（{@code @ConditionalOnClass(HttpServletRequest.class)}），
 * <b>不要求应用是 Web 应用</b>：{@code spring.main.web-application-type=none} 的纯后台/定时任务应用
 * 同样会装配，内部凭证照常携带。无请求上下文时身份头透传自然跳过（见
 * {@link #currentRequest()}），不影响凭证注入。</p>
 *
 * <p><strong>已知边界</strong>：本类方法签名引用 {@link HttpServletRequest}，classpath 完全没有
 * Servlet API 时（本模块对 {@code jakarta.servlet-api} 是 optional 依赖）无法装配，需业务方自行提供
 * 等价的 {@code RequestInterceptor}；此时「内部凭证携带」能力不可用。</p>
 *
 * <p><strong>凭证安全</strong>：{@code internalToken} 是真实凭据，本类<b>不把它写进任何日志、
 * 异常消息或 {@code toString}</b>；启动告警只说明「未配置」这一事实，不含凭证值。
 * 但 Feign 自身在 {@code logger-level=headers}/{@code full} 时会打印<b>全部</b>请求头（含本头，
 * feign-core 13.6.1 的 {@code Logger.logRequest} 实测），建议保持 {@code none}/{@code basic}；
 * 如需彻底屏蔽可覆写 {@code feign.Logger#shouldLogRequestHeader(String)}。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class FeignHeaderInterceptor implements RequestInterceptor {

    /**
     * 默认的内部调用凭证请求头名：{@code X-Internal-Token}。
     *
     * <p><strong>必须与 security 守卫侧逐字一致</strong>：守卫读的是
     * {@code ypbin.security.internal-token.header-name}（其默认值同为 {@code X-Internal-Token}，
     * 见 {@code InternalTokenProperties.DEFAULT_HEADER_NAME}）。两侧不一致的后果不是报错，而是
     * 「凭证携带了但守卫读不到」⇒ 全部内部调用被 401 拒绝。本模块<b>不依赖</b> security 模块
     * （避免基座模块互相耦合），故这里以字面量声明并在注释中固定该约定；改动任一侧都必须同步另一侧。</p>
     *
     * <p>常量定义在携带侧（本类）而非配置类，是为了让依赖方向保持「配置类 → 拦截器」单向
     * （配置类的默认值引用本常量），不引入包级循环依赖。</p>
     */
    public static final String DEFAULT_INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private static final Logger log = LoggerFactory.getLogger(FeignHeaderInterceptor.class);

    private final List<String> propagateHeaders;

    /** 规范化后的身份头名单 */
    private final Set<String> identityHeaders;

    /** 可信来源标记头名（规范化），为空表示不校验来源 */
    private final String trustedSourceHeader;

    /** 可信来源标记期望值，为空表示不校验来源 */
    private final String trustedSourceToken;

    /** 内部凭证头名（构造期已归一化，空白回退默认头名，保证非空） */
    private final String internalTokenHeader;

    /** 内部凭证值；空串表示不注入（不持有 {@code null}，避免空值传播） */
    private final String internalToken;

    public FeignHeaderInterceptor(List<String> propagateHeaders, List<String> identityHeaders,
            String trustedSourceHeader, String trustedSourceToken) {
        this(propagateHeaders, identityHeaders, trustedSourceHeader, trustedSourceToken, false);
    }

    /**
     * 兼容构造：不携带内部凭证。
     *
     * @param propagateHeaders     需透传的请求头白名单
     * @param identityHeaders      需来源可信才透传的身份头
     * @param trustedSourceHeader  可信来源标记头名
     * @param trustedSourceToken   可信来源标记期望值（为空表示不校验来源）
     * @param requireTrustedSource 是否要求必须配置标记（true 时未配置直接启动失败）
     */
    public FeignHeaderInterceptor(List<String> propagateHeaders, List<String> identityHeaders,
            String trustedSourceHeader, String trustedSourceToken, boolean requireTrustedSource) {
        this(propagateHeaders, identityHeaders, trustedSourceHeader, trustedSourceToken, requireTrustedSource,
            DEFAULT_INTERNAL_TOKEN_HEADER, "");
    }

    /**
     * 完整构造。
     *
     * @param propagateHeaders     需透传的请求头白名单
     * @param identityHeaders      需来源可信才透传的身份头
     * @param trustedSourceHeader  可信来源标记头名
     * @param trustedSourceToken   可信来源标记期望值（为空表示不校验来源）
     * @param requireTrustedSource 是否要求必须配置标记（true 时未配置直接启动失败）
     * @param internalTokenHeader  内部凭证请求头名（空白回退默认头名）
     * @param internalToken        内部凭证值（空即不注入；真实凭据，绝不入日志）
     */
    public FeignHeaderInterceptor(List<String> propagateHeaders, List<String> identityHeaders,
            String trustedSourceHeader, String trustedSourceToken, boolean requireTrustedSource,
            @Nullable String internalTokenHeader, @Nullable String internalToken) {
        this.propagateHeaders = propagateHeaders;
        this.identityHeaders = (identityHeaders == null ? List.<String>of() : identityHeaders).stream()
            .map(FeignHeaderInterceptor::normalizeHeaderName)
            .collect(Collectors.toUnmodifiableSet());
        this.trustedSourceHeader = trustedSourceHeader;
        this.trustedSourceToken = trustedSourceToken;
        this.internalTokenHeader = (internalTokenHeader == null || internalTokenHeader.isBlank())
            ? DEFAULT_INTERNAL_TOKEN_HEADER : internalTokenHeader;
        this.internalToken = internalToken == null ? "" : internalToken;
        validateTrustedSource(requireTrustedSource);
        warnWhenInternalTokenMissing();
    }

    /**
     * 可信来源标记未配置时的告警/失败（保持既有语义：默认只告警，可配置为启动失败）。
     *
     * @param requireTrustedSource 是否要求必须配置
     */
    private void validateTrustedSource(boolean requireTrustedSource) {
        if (hasText(this.trustedSourceToken)) {
            return;
        }
        String message = "[ypbin-starter] 未配置 ypbin.cloud.feign.trusted-source-token，"
            + "身份头将不做来源校验直接透传；若服务可被外部直连，请配置该值并在网关签发 "
            + this.trustedSourceHeader + " 标记，防止伪造身份经 Feign 放大越权";
        if (requireTrustedSource) {
            throw new IllegalStateException(message + "（已开启 "
                + "ypbin.cloud.feign.require-trusted-source，拒绝以不安全配置启动）");
        }
        log.warn(message);
    }

    /**
     * 内部凭证未配置时的启动告警（只说明「未配置」，绝不含凭证值）。
     *
     * <p>与可信来源标记同一风格：默认只告警、不阻断启动——未使用内部端点的服务无需该凭证；
     * 但一旦下游开启守卫，缺失凭证的内部调用会被 fail-closed 拒绝，故必须在启动期就提示。</p>
     */
    private void warnWhenInternalTokenMissing() {
        if (hasText(this.internalToken)) {
            return;
        }
        log.warn("[ypbin-starter] 未配置 ypbin.cloud.feign.internal-token，Feign 请求不会携带内部调用凭证；"
            + "若下游开启了 ypbin.security.internal-token.guard-enabled，调用其内部端点会被 401 拒绝。"
            + "（本告警不含凭证值，凭证请经环境变量注入）");
    }

    @Override
    public void apply(RequestTemplate template) {
        Set<String> existingHeaders = template.headers().keySet().stream()
            .map(FeignHeaderInterceptor::normalizeHeaderName)
            .collect(Collectors.toSet());
        // 内部凭证先注入：与入站请求上下文无关，定时任务/异步线程的调用同样必须携带
        injectInternalToken(template, existingHeaders);
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return;
        }
        boolean identityTrusted = isIdentitySourceTrusted(request);
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
     * 把配置的内部调用凭证写入 Feign 请求头。
     *
     * <p>模板（下游客户端/其它拦截器）已显式设置同名头时<b>不覆盖</b>；注入后把该头名登记进
     * {@code existingHeaders}，使后续白名单透传不会再追加一个同名值——否则若把内部凭证头也配进
     * {@code propagate-headers}，就可能出现「我们的凭证 + 入站伪造值」两个 header 值。</p>
     *
     * @param template        当前请求模板
     * @param existingHeaders 已存在的头名（规范化，会被就地更新）
     */
    private void injectInternalToken(RequestTemplate template, Set<String> existingHeaders) {
        if (!hasText(internalToken)) {
            return;
        }
        String normalized = normalizeHeaderName(internalTokenHeader);
        if (existingHeaders.contains(normalized)) {
            return;
        }
        template.header(internalTokenHeader, internalToken);
        existingHeaders.add(normalized);
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

    /**
     * 读取当前线程绑定的 Servlet 请求；无请求上下文时返回 {@code null}（调用方据此跳过身份头透传）。
     *
     * <p><b>非 Web 应用下的空安全性</b>：纯后台/定时任务应用从未绑定请求属性，
     * {@link RequestContextHolder#getRequestAttributes()} 返回 {@code null}，
     * {@code null instanceof ...} 恒为 {@code false}，故直接返回 {@code null}，不抛异常；
     * 绑定的是非 Servlet 的 {@link org.springframework.web.context.request.RequestAttributes}
     * 实现时同样走这一分支。因此本方法在无 Web 上下文时是空安全的——内部凭证注入不依赖它
     * （见 {@link #apply(RequestTemplate)}）。</p>
     *
     * <p>兜底 {@code catch} 只针对极端场景（如 scoped proxy 的 request 作用域未激活时读取失败）；
     * 此时同样安全跳过，但异常必须留痕（debug 级、含完整堆栈），否则「身份头静默丢失」无从排查。</p>
     *
     * @return 当前请求；无请求上下文时为 {@code null}
     */
    @Nullable
    private HttpServletRequest currentRequest() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                return attrs.getRequest();
            }
        } catch (Exception ex) {
            log.debug("[ypbin-starter] 读取请求上下文失败，本次 Feign 调用跳过入站请求头透传（内部凭证不受影响）", ex);
        }
        return null;
    }

    private static String normalizeHeaderName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}

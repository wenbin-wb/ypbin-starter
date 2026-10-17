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
package cn.ypbin.starter.gateway.filter;

import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import cn.ypbin.starter.core.util.LogSanitizer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import org.springframework.web.util.pattern.PatternParseException;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * 内部端点路径阻断全局过滤器（GitHub issue #96 的网关侧要求）。
 *
 * <p>把服务侧的 {@code /internal/**} 端点挡在网关之外：命中 {@link InternalPathBlockProperties#getPathPatterns()}
 * 时<b>不转发</b>，直接写回一个与既有 404 一致的信封响应（{@link R} + {@link GlobalErrorCode#NOT_FOUND}），
 * 响应体为固定文案、<b>不回显请求路径</b>，故调用方无法据响应判断某个内部端点是否存在。</p>
 *
 * <p><strong>顺序</strong>：{@link #getOrder()} = {@link Ordered#HIGHEST_PRECEDENCE}+1，与
 * {@code HeaderSanitizeGlobalFilter} 同级、早于 {@code GatewayAuthGlobalFilter}
 * （{@code HIGHEST_PRECEDENCE+2}）与限流过滤器：命中即拒绝，<b>不进入鉴权链路</b>——既避免为探测内部
 * 路径的流量付出鉴权开销，也不让内部端点路径出现在鉴权失败的日志/指标里。本过滤器不依赖请求 ID
 * 或身份头清洗的结果（只读请求路径、直接写响应），故与 {@code HeaderSanitizeGlobalFilter} 同序并列
 * 无需再排先后。</p>
 *
 * <p><strong>日志级别为 {@code debug}</strong>：命中本过滤器是<b>客户端可任意触发</b>的（构造任意
 * {@code /internal/...} 请求即可），若用 {@code warn} 则等于给外部提供了一个无限量的日志写入放大器
 * （日志膨胀、磁盘打满），且完整内部路径会进入生产日志、暴露内部 API 面。故路径只在 {@code debug} 下
 * 记录（经 {@link LogSanitizer} 处理，防日志注入），需要安全可见性的部署应显式调高本类日志级别或
 * 改用计数类指标观测。</p>
 *
 * <p><strong>路径模式</strong>：详见 {@link InternalPathBlockProperties}。要点：普通模式走
 * WebFlux {@link PathPattern} 语义（与 Gateway 路由谓词同口径）；以 {@link #ANY_DEPTH_PREFIX}
 * 开头的模式按「任意层级前缀」解释（{@link PathPattern} 不允许两个 {@code **}，实测无法直接表达
 * 默认模式 {@link InternalPathBlockProperties#DEFAULT_PATH_PATTERN}），实现方式是对
 * {@link PathContainer} 的每个分段边界做后缀匹配，因此顶层 {@code /internal/x} 与嵌套
 * {@code /iot/internal/x} 都被覆盖。</p>
 *
 * <p>路径模式的编译失败在<b>启动期</b>抛出异常（fail-fast）：安全过滤器"启动了但某条模式静默不生效"
 * 是本仓明令禁止的形态。</p>
 *
 * <p><strong>匹配的两个加固（独立验收提出的绕过形态）</strong>：</p>
 * <ul>
 *     <li><b>编码分隔符</b>：{@link PathContainer} 只在真实 {@code '/'} 处分段，故额外用原始路径把
 *     {@code %2F}/{@code %5C} 归一化后判一次（见 {@link #matchesEncodedSeparatorVariant(ServerWebExchange)}）
 *     ——否则 {@code /iot/internal%5Ckeys} 会被直接转发，下游 Tomcat 默认把 {@code '\'} 当分隔符，
 *     等于绕过本过滤器；</li>
 *     <li><b>大小写</b>：{@link PathPatternParser#setCaseSensitive(boolean)} 置 false，避免
 *     {@code /iot/INTERNAL/x} 换个大小写即绕过（网关侧拦得宽一点无副作用，都是 404）。</li>
 * </ul>
 *
 * <p><strong>取哪条路径（实测口径）</strong>：本过滤器取
 * {@code exchange.getRequest().getPath().pathWithinApplication()}，与同模块的
 * {@code GatewayAuthGlobalFilter}（白名单匹配）一致。Gateway 5.0.3 的
 * {@code PathRoutePredicateFactory} 实际取的是 {@code getURI().getRawPath()}（javap 实测），
 * 二者仅在宿主配置了 {@code spring.webflux.base-path} 时才有差别：路由匹配看<b>未剥离 base-path</b>
 * 的原始路径，本过滤器看<b>剥离后</b>的应用内路径。本过滤器按「任意层级」匹配，故
 * {@code /gw/internal/x} 这类带 base-path 的前缀同样会被拦；唯一例外是 base-path 自身叫
 * {@code /internal}（病态配置）时应用内路径已不含该段。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
public class InternalPathBlockGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(InternalPathBlockGlobalFilter.class);

    /** 「任意层级前缀」约定的识别前缀（见 {@link InternalPathBlockProperties} 的语义说明）。 */
    private static final String ANY_DEPTH_PREFIX = "/**/";

    /** 任意层级前缀去掉后尾模式为空时的兜底：匹配全部路径。 */
    private static final String MATCH_ALL_TAIL = "**";

    /** HTTP 状态码下界（含）；RFC 9110 的状态码为三位数 100–599。 */
    private static final int MIN_HTTP_STATUS = 100;

    /**
     * HTTP 状态码上界（含）。
     *
     * <p>必须显式校验上界：实测 {@code HttpStatusCode.valueOf(999)} <b>不抛异常</b>
     * （只校验「三位正整数」），若只依赖它，非法配置会被静默接受。</p>
     */
    private static final int MAX_HTTP_STATUS = 599;

    /**
     * 编码形式的路径分隔符（{@code %2F} / {@code %5C}，大小写不敏感）。
     *
     * <p>用于「编码分隔符变体」判定：{@link PathContainer} 只在真实 {@code '/'} 处分段，若不额外归一化，
     * {@code /iot/internal%5Ckeys} 会绕过本过滤器并被下游（Tomcat 默认把 {@code '\'} 当分隔符）解码成
     * {@code /iot/internal/keys}。详见 {@link #matchesEncodedSeparatorVariant(ServerWebExchange)}。</p>
     */
    private static final Pattern ENCODED_SEPARATOR = Pattern.compile("%2f|%5c", Pattern.CASE_INSENSITIVE);

    /**
     * 信封序列化失败时的兜底响应体：code/message 与 {@code R.fail(NOT_FOUND)} 一致（不含
     * {@code data}/{@code timestamp} 字段），且<b>不含任何输入值</b>。
     *
     * <p>正常路径下这段常量用不到；保留它是为了让「序列化器异常」也不至于把 Jackson 的错误细节
     * 或请求路径泄露给调用方（宁可返回固定文案，也不返回 500 或空响应）。</p>
     */
    private static final String FALLBACK_BODY = "{\"code\":" + GlobalErrorCode.NOT_FOUND.getCode()
        + ",\"message\":\"" + GlobalErrorCode.NOT_FOUND.getMessage() + "\",\"success\":false}";

    /** 普通路径模式（{@link PathPattern} 直接匹配） */
    private final List<PathPattern> pathPatterns;

    /** 「任意层级前缀」路径模式（去前缀后编译，按分段后缀匹配） */
    private final List<PathPattern> anyDepthPathPatterns;

    /** 命中时写回的 HTTP 状态码（信封一律为 404 语义，本项只控制 HTTP 层） */
    private final HttpStatusCode notFoundStatus;

    /** 命中时写回的响应体字节（构造期一次性生成，避免每个被拒请求重复序列化） */
    private final byte[] notFoundBody;

    /**
     * 构造过滤器。
     *
     * @param properties   配置项（路径模式与状态码）
     * @param objectMapper 信封序列化器（与网关其余响应同口径，保证字段命名一致）
     * @throws IllegalArgumentException 路径模式无法编译，或状态码非法（启动期 fail-fast）
     */
    public InternalPathBlockGlobalFilter(InternalPathBlockProperties properties, ObjectMapper objectMapper) {
        CompiledPatterns compiled = compile(properties.getPathPatterns());
        this.pathPatterns = compiled.directPatterns();
        this.anyDepthPathPatterns = compiled.anyDepthPatterns();
        this.notFoundStatus = resolveStatus(properties.getNotFoundStatus());
        this.notFoundBody = serializeNotFoundBody(objectMapper);
        if (pathPatterns.isEmpty() && anyDepthPathPatterns.isEmpty()) {
            // 显式配置成空清单可以是刻意为之，但它同时是「配置中心覆盖掉默认值」的典型误配形态，
            // 而后果是安全过滤器静默变成空操作，故必须留一条启动告警而不是无声通过。
            log.warn("[ypbin-starter] {}.enabled=true 但没有任何有效路径模式，"
                + "内部端点路径阻断实际不拦截任何请求；若并非刻意配置，请检查是否被其它配置源覆盖了默认值",
                InternalPathBlockProperties.PREFIX);
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        PathContainer path = exchange.getRequest().getPath().pathWithinApplication();
        if (!matches(path) && !matchesEncodedSeparatorVariant(exchange)) {
            return chain.filter(exchange);
        }
        if (log.isDebugEnabled()) {
            log.debug("[内部路径阻断] 已拒绝：status={} method={} path={}", notFoundStatus.value(),
                exchange.getRequest().getMethod(), LogSanitizer.sanitize(path.value()));
        }
        return writeNotFound(exchange.getResponse());
    }

    /**
     * 编码分隔符变体匹配：把<b>原始路径</b>里的 {@code %2F} / {@code %5C} 视作路径分隔符后再判一次。
     *
     * <p><strong>为什么必须补这一层</strong>：{@link PathContainer} 只在<b>真实</b> {@code '/'} 处分段，
     * 编码后的分隔符会留在段内 ⇒ {@code /iot/internal%5Ckeys} 不会命中 {@code /internal/**} 而被直接转发；
     * 而 Tomcat 默认 {@code encodedReverseSolidusHandling=DECODE} 且 {@code CoyoteAdapter.normalize} 把
     * {@code '\'} 与 {@code '/'} 同等当作分隔符（tomcat-embed-core 11.0.24 字节码实测）⇒ 下游解码后即可
     * 直达 {@code /internal/keys}，等于绕过了本过滤器。{@code %2F} 在 Tomcat 默认 {@code REJECT}，但其它
     * 容器/反代（按 {@code decodeURI} 语义）会解析为分隔符，同样必须覆盖。</p>
     *
     * <p>归一化只用于<b>判定</b>：命中即拒绝；不命中仍按原始路径转发，不改变任何正常流量语义。</p>
     *
     * @param exchange 当前交换
     * @return 归一化后是否命中阻断模式
     */
    private boolean matchesEncodedSeparatorVariant(ServerWebExchange exchange) {
        String rawPath = exchange.getRequest().getURI().getRawPath();
        if (rawPath == null || rawPath.isEmpty()) {
            return false;
        }
        String normalized = ENCODED_SEPARATOR.matcher(rawPath).replaceAll("/");
        if (normalized.equals(rawPath)) {
            return false;
        }
        return matches(PathContainer.parsePath(normalized, PathContainer.Options.HTTP_PATH));
    }

    /**
     * 路径是否命中任一阻断模式。
     *
     * @param path 应用内路径（{@link PathContainer} 口径，编码解码语义与 Gateway 路由谓词一致；
     *             base-path 差异见类注释）
     * @return 命中任一模式时为 {@code true}
     */
    private boolean matches(PathContainer path) {
        for (PathPattern pattern : pathPatterns) {
            if (pattern.matches(path)) {
                return true;
            }
        }
        for (PathPattern pattern : anyDepthPathPatterns) {
            if (matchesAnyDepth(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 「任意层级前缀」匹配：对请求路径的每个分段边界取后缀，逐一与去前缀后的模式比对。
     *
     * <p>等价于 Ant 用「任意层级」通配前缀加尾模式的写法（{@code **} 匹配零到多段前缀），但对
     * <b>顶层路径</b>也成立：{@code /internal/x} 的后缀 {@code /internal/x} 命中
     * {@code /internal/**}。只在 {@link PathContainer.Separator}（即分段边界）处切分，故
     * {@code /xinternal/y} 这类「前缀粘连」的路径不会被误拦。</p>
     *
     * @param pattern 去掉「任意层级前缀」后编译的模式（如 {@code /internal/**}）
     * @param path    应用内路径
     * @return 任一分段边界起的后缀命中时为 {@code true}
     */
    private static boolean matchesAnyDepth(PathPattern pattern, PathContainer path) {
        List<PathContainer.Element> elements = path.elements();
        for (int index = 0; index < elements.size(); index++) {
            if (elements.get(index) instanceof PathContainer.Separator && pattern.matches(path.subPath(index))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 写回统一的 404 信封响应（固定文案、无请求路径回显）。
     *
     * @param response 当前响应
     * @return 写响应完成信号
     */
    private Mono<Void> writeNotFound(ServerHttpResponse response) {
        response.setStatusCode(notFoundStatus);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(notFoundBody);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    /**
     * 编译配置的路径模式。
     *
     * @param rawPatterns 配置的原始模式清单（允许含空白项，将被忽略）
     * @return 普通模式与「任意层级前缀」模式两组编译结果
     * @throws IllegalArgumentException 任一模式无法编译（含「出现两个 **」但未按任意层级前缀书写的写法）
     */
    private static CompiledPatterns compile(List<String> rawPatterns) {
        PathPatternParser parser = new PathPatternParser();
        // 大小写不敏感：/iot/INTERNAL/x 与 /iot/internal/x 指向同一端点（容器与多数下游都不区分大小写），
        // 只看小写形态会留下一条"换个大小写即可绕过"的通道；网关侧拦得宽一点没有副作用（都是 404）
        parser.setCaseSensitive(false);
        List<PathPattern> directPatterns = new ArrayList<>();
        List<PathPattern> anyDepthPatterns = new ArrayList<>();
        for (String raw : rawPatterns) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String pattern = raw.trim();
            if (pattern.startsWith(ANY_DEPTH_PREFIX)) {
                String tail = pattern.substring(ANY_DEPTH_PREFIX.length());
                anyDepthPatterns.add(parse(parser, "/" + (tail.isBlank() ? MATCH_ALL_TAIL : tail), raw));
                continue;
            }
            directPatterns.add(parse(parser, pattern, raw));
        }
        return new CompiledPatterns(List.copyOf(directPatterns), List.copyOf(anyDepthPatterns));
    }

    /**
     * 解析单个路径模式，失败时给出可操作的报错（而非静默跳过）。
     *
     * @param parser  路径模式解析器
     * @param pattern 归一化后的待解析模式
     * @param raw     配置里的原始写法（用于报错定位）
     * @return 编译后的路径模式
     * @throws IllegalArgumentException 模式非法
     */
    private static PathPattern parse(PathPatternParser parser, String pattern, String raw) {
        try {
            return parser.parse(pattern);
        } catch (PatternParseException ex) {
            throw new IllegalArgumentException("[ypbin-starter] " + InternalPathBlockProperties.PREFIX
                + ".path-patterns 取值非法：path=" + raw + "（" + ex.getMessage() + "）。Spring 的"
                + " PathPattern 只允许出现一个 '**'；需要「任意层级前缀」时请以 '/**/' 开头书写"
                + "（例如表示任意层级下的 /internal/** 时写 '/**/internal/**'，该写法含顶层；"
                + "不要写 '/a/**/internal/**' 这类含两个 '**' 的写法）。", ex);
        }
    }

    /**
     * 解析并校验配置的 HTTP 状态码。
     *
     * @param status 配置的状态码
     * @return 校验通过的状态码
     * @throws IllegalArgumentException 状态码不在 100–599 区间（启动期 fail-fast）
     */
    private static HttpStatusCode resolveStatus(int status) {
        if (status < MIN_HTTP_STATUS || status > MAX_HTTP_STATUS) {
            throw new IllegalArgumentException("[ypbin-starter] " + InternalPathBlockProperties.PREFIX
                + ".not-found-status 取值非法：" + status + " 不是有效的 HTTP 状态码（应在 "
                + MIN_HTTP_STATUS + "–" + MAX_HTTP_STATUS + " 之间）。");
        }
        return HttpStatusCode.valueOf(status);
    }

    /**
     * 生成命中响应体。
     *
     * @param objectMapper 容器配置的序列化器
     * @return 响应体字节（序列化失败时退化为固定文案，绝不向调用方泄露异常细节）
     */
    private static byte[] serializeNotFoundBody(ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsBytes(R.fail(GlobalErrorCode.NOT_FOUND));
        } catch (JacksonException ex) {
            log.error("[内部路径阻断] 404 信封序列化失败，改用固定文案", ex);
            return FALLBACK_BODY.getBytes(StandardCharsets.UTF_8);
        }
    }

    /**
     * 两组编译结果。
     *
     * @param directPatterns   普通路径模式
     * @param anyDepthPatterns 「任意层级前缀」路径模式
     */
    private record CompiledPatterns(List<PathPattern> directPatterns, List<PathPattern> anyDepthPatterns) {
    }
}

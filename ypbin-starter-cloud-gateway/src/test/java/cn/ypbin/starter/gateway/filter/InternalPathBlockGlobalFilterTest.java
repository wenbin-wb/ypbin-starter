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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ypbin.starter.gateway.auth.GatewayAuthProvider;
import cn.ypbin.starter.gateway.auth.GatewayAuthResult;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPatternParser;
import org.springframework.web.util.pattern.PatternParseException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link InternalPathBlockGlobalFilter} 单元测试（GitHub issue #96）。
 *
 * <p><b>关于 {@code /**&#47;internal/**} 的实测前提</b>：本用例用
 * {@link #pathPatternParserCannotExpressAnyDepthInternalPattern()} 把「Spring
 * {@code PathPattern} 只允许一个 {@code **}」这一事实钉住——它正是本过滤器引入「任意层级前缀」
 * 约定的原因。若将来 Spring 放宽该限制，此用例会失败，提示可以简化实现（而非行为回归）。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
class InternalPathBlockGlobalFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 记录过滤链是否被调用（被调用 = 请求会继续转发） */
    private static final class ChainProbe implements GatewayFilterChain {

        private final AtomicBoolean invoked = new AtomicBoolean();

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            invoked.set(true);
            return Mono.empty();
        }
    }

    private static InternalPathBlockGlobalFilter defaultFilter() {
        return new InternalPathBlockGlobalFilter(new InternalPathBlockProperties(), new ObjectMapper());
    }

    /**
     * 构造 GET 交换。
     *
     * <p>刻意用 {@link URI} 直接构造请求，而不用 {@code MockServerHttpRequest.get(String)}
     * ——实测后者会对 URL 模板再编码一次（{@code /iot/%69nternal/keys} 变成
     * {@code /iot/%2569nternal/keys}），从而<strong>测不出</strong>真实的编码绕过场景。</p>
     *
     * @param rawPath 原始请求路径（可含百分号编码）
     * @return mock 交换
     */
    private static MockServerWebExchange get(String rawPath) {
        return MockServerWebExchange.from(
            MockServerHttpRequest.method(HttpMethod.GET, URI.create(rawPath)).build());
    }

    /**
     * 实测前提：{@code PathPattern} 不允许两个 {@code **}，故默认模式无法直接解析。
     */
    @Test
    void pathPatternParserCannotExpressAnyDepthInternalPattern() {
        assertThatThrownBy(() -> new PathPatternParser().parse("/**/internal/**"))
            .isInstanceOf(PatternParseException.class)
            .hasMessageContaining("Multiple");
    }

    @Test
    void shouldBlockTopLevelInternalPathAndNotForward() {
        InternalPathBlockGlobalFilter filter = defaultFilter();
        MockServerWebExchange exchange = get("/internal/secret");
        ChainProbe chain = new ChainProbe();

        filter.filter(exchange, chain).block();

        assertThat(chain.invoked).as("命中后绝不能再进入过滤链（否则等于放行）").isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldBlockNestedInternalPath() {
        InternalPathBlockGlobalFilter filter = defaultFilter();
        MockServerWebExchange exchange = get("/iot/internal/keys");
        ChainProbe chain = new ChainProbe();

        filter.filter(exchange, chain).block();

        assertThat(chain.invoked).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldBlockDeeplyNestedInternalPath() {
        InternalPathBlockGlobalFilter filter = defaultFilter();
        MockServerWebExchange exchange = get("/a/b/internal/c/d");
        ChainProbe chain = new ChainProbe();

        filter.filter(exchange, chain).block();

        assertThat(chain.invoked).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldBlockPercentEncodedInternalSegment() {
        // 与 Gateway 路由谓词同口径：路径匹配在解码后的分段上做，编码绕过必须同样被拦
        InternalPathBlockGlobalFilter filter = defaultFilter();
        MockServerWebExchange exchange = get("/iot/%69nternal/keys");
        ChainProbe chain = new ChainProbe();

        filter.filter(exchange, chain).block();

        assertThat(chain.invoked).isFalse();
    }

    @Test
    void shouldBlockEncodedSeparatorBeforeInternalSegment() {
        // 独立验收发现的绕过：PathContainer 只在真实 '/' 处分段，'/iot/internal%5Ckeys' 不会被
        // '/internal/**' 命中而被直接转发；而 Tomcat 默认把 '\' 当分隔符解码 → 下游直达
        // /iot/internal/keys。故必须把 %2F/%5C 归一化后再判一次。
        InternalPathBlockGlobalFilter filter = defaultFilter();

        for (String rawPath : List.of("/iot/internal%5Ckeys", "/iot/internal%2Fkeys",
                "/internal%5Csecret", "/iot/internal%2fkeys")) {
            ChainProbe chain = new ChainProbe();
            filter.filter(get(rawPath), chain).block();
            assertThat(chain.invoked).as("编码分隔符绕过必须被拦：%s", rawPath).isFalse();
        }
    }

    @Test
    void shouldBlockCaseVariantOfInternalSegment() {
        // 大小写变体：PathPattern 字面量默认大小写敏感，'/iot/INTERNAL/x' 会绕过；下游容器大小写不敏感
        InternalPathBlockGlobalFilter filter = defaultFilter();
        ChainProbe chain = new ChainProbe();

        filter.filter(get("/iot/INTERNAL/x"), chain).block();

        assertThat(chain.invoked).isFalse();
    }

    @Test
    void shouldNotBlockNonInternalPath() {
        InternalPathBlockGlobalFilter filter = defaultFilter();
        MockServerWebExchange exchange = get("/api/echo");
        ChainProbe chain = new ChainProbe();

        filter.filter(exchange, chain).block();

        assertThat(chain.invoked).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void shouldNotBlockPathWithSegmentContainingInternalAsPrefix() {
        // 「前缀粘连」不得误伤：xinternal / internalized 都不是 internal 分段
        InternalPathBlockGlobalFilter filter = defaultFilter();
        ChainProbe chainA = new ChainProbe();
        filter.filter(get("/iot/xinternal/y"), chainA).block();
        assertThat(chainA.invoked).isTrue();

        ChainProbe chainB = new ChainProbe();
        filter.filter(get("/iot/internalized/y"), chainB).block();
        assertThat(chainB.invoked).isTrue();
    }

    @Test
    void shouldNotBlockInternalLookingPathAtRootOnlySegment() {
        // /internal 之后无子路径：默认模式 `/**/internal/**` 的尾 `**` 匹配零段，应拦
        InternalPathBlockGlobalFilter filter = defaultFilter();
        ChainProbe chain = new ChainProbe();

        filter.filter(get("/internal"), chain).block();

        assertThat(chain.invoked).isFalse();
    }

    @Test
    void responseBodyShouldNotEchoPathAndShouldCarryCanonicalNotFoundEnvelope() {
        InternalPathBlockGlobalFilter filter = defaultFilter();
        MockServerWebExchange exchange = get("/iot/internal/top-secret");

        filter.filter(exchange, new ChainProbe()).block();

        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).as("响应体不得回显内部路径细节").doesNotContain("internal", "top-secret", "/iot");
        assertThat(body).contains("\"code\":404").contains("\"success\":false");
    }

    @Test
    void shouldSupportCustomPathPatterns() {
        InternalPathBlockProperties properties = new InternalPathBlockProperties();
        properties.setPathPatterns(List.of("/api/private/**"));
        InternalPathBlockGlobalFilter filter = new InternalPathBlockGlobalFilter(properties, objectMapper);

        ChainProbe blocked = new ChainProbe();
        filter.filter(get("/api/private/x"), blocked).block();
        assertThat(blocked.invoked).isFalse();

        // 自定义后不再拦截 /internal/**（覆盖而非叠加，语义可预期）
        ChainProbe allowed = new ChainProbe();
        filter.filter(get("/internal/x"), allowed).block();
        assertThat(allowed.invoked).isTrue();
    }

    @Test
    void shouldSupportCustomNotFoundStatus() {
        InternalPathBlockProperties properties = new InternalPathBlockProperties();
        properties.setNotFoundStatus(HttpStatus.FORBIDDEN.value());
        InternalPathBlockGlobalFilter filter = new InternalPathBlockGlobalFilter(properties, objectMapper);
        MockServerWebExchange exchange = get("/internal/x");

        filter.filter(exchange, new ChainProbe()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldSupportHttpOkStatusForProjectEnvelopeConvention() {
        // 本仓约定「HTTP 200 + R.code 区分错误」，故 200 必须是合法取值
        InternalPathBlockProperties properties = new InternalPathBlockProperties();
        properties.setNotFoundStatus(HttpStatus.OK.value());
        InternalPathBlockGlobalFilter filter = new InternalPathBlockGlobalFilter(properties, objectMapper);
        MockServerWebExchange exchange = get("/internal/x");

        filter.filter(exchange, new ChainProbe()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"code\":404");
    }

    @Test
    void shouldFailFastOnPatternWithTwoDoubleStars() {
        InternalPathBlockProperties properties = new InternalPathBlockProperties();
        properties.setPathPatterns(List.of("/iot/**/internal/**"));

        assertThatThrownBy(() -> new InternalPathBlockGlobalFilter(properties, objectMapper))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("path-patterns")
            .hasMessageContaining("/**/");
    }

    @Test
    void shouldFailFastOnInvalidNotFoundStatus() {
        InternalPathBlockProperties properties = new InternalPathBlockProperties();
        properties.setNotFoundStatus(999);

        assertThatThrownBy(() -> new InternalPathBlockGlobalFilter(properties, objectMapper))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not-found-status");
    }

    @Test
    void nullPathPatternsShouldBlockNothingInsteadOfFailingAtRuntime() {
        InternalPathBlockProperties properties = new InternalPathBlockProperties();
        properties.setPathPatterns(null);

        InternalPathBlockGlobalFilter filter = new InternalPathBlockGlobalFilter(properties, objectMapper);
        ChainProbe chain = new ChainProbe();
        filter.filter(get("/internal/x"), chain).block();

        assertThat(properties.getPathPatterns()).isEmpty();
        assertThat(chain.invoked).isTrue();
    }

    /**
     * 「配了开关但没有任何有效模式」必须留下启动告警：这是安全过滤器静默变成空操作的最常见形态
     * （配置中心把默认清单覆盖成了空列表），不能无声通过。
     */
    @Test
    void shouldWarnWhenNoEffectivePatternConfigured() {
        Logger logger = (Logger) LoggerFactory.getLogger(InternalPathBlockGlobalFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            InternalPathBlockProperties properties = new InternalPathBlockProperties();
            properties.setPathPatterns(List.of("  "));

            new InternalPathBlockGlobalFilter(properties, objectMapper);

            assertThat(appender.list)
                .anyMatch(event -> event.getLevel() == Level.WARN
                    && event.getFormattedMessage().contains("不拦截任何请求"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void shouldRunBeforeAuthFilter() {
        GatewayAuthProvider authProvider = exchange -> Mono.just(GatewayAuthResult.success());
        GatewayAuthGlobalFilter authFilter = new GatewayAuthGlobalFilter(
            authProvider, objectMapper, List.of(), "X-Gateway-Signed", "");

        assertThat(defaultFilter().getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1);
        assertThat(authFilter.getOrder())
            .as("阻断必须不晚于鉴权过滤器，避免为探测内部路径的流量付出鉴权开销")
            .isGreaterThanOrEqualTo(defaultFilter().getOrder());
    }
}

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import feign.RequestTemplate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@link FeignHeaderInterceptor} 单元测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class FeignHeaderInterceptorTest {

    /** 内部调用凭证测试值：刻意用可被字符串搜索命中的形态，便于断言「绝不出现在日志/toString」 */
    private static final String INTERNAL_TOKEN = "internal-token-8f3a1c";

    private static final String INTERNAL_HEADER = FeignHeaderInterceptor.DEFAULT_INTERNAL_TOKEN_HEADER;

    /** 兼容构造：不启用来源校验（等价旧行为） */
    private static FeignHeaderInterceptor noSourceCheck(List<String> headers) {
        return new FeignHeaderInterceptor(headers, List.of(), "X-Gateway-Signed", "");
    }

    /** 带内部凭证的构造：来源标记已配置，避免干扰日志断言 */
    private static FeignHeaderInterceptor withInternalToken(List<String> propagateHeaders, String headerName,
            String token) {
        return new FeignHeaderInterceptor(propagateHeaders, List.of(), "X-Gateway-Signed", "signed-token",
            false, headerName, token);
    }

    /** 捕获指定 logger 的全部事件（级别放到 TRACE，避免漏掉将来的 debug 输出） */
    private static ListAppender<ILoggingEvent> attachAppender(Logger logger) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldPropagateConfiguredHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "req-1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        FeignHeaderInterceptor interceptor = noSourceCheck(List.of("X-Request-Id"));
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get("X-Request-Id")).containsExactly("req-1");
    }

    @Test
    void shouldNotDuplicateHeaderByCaseInsensitiveName() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "from-request");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        FeignHeaderInterceptor interceptor = noSourceCheck(List.of("X-Request-Id"));
        RequestTemplate template = new RequestTemplate();
        template.header("x-request-id", "existing");

        interceptor.apply(template);

        assertThat(template.headers()).containsOnlyKeys("x-request-id");
        assertThat(template.headers().get("x-request-id")).containsExactly("existing");
    }

    @Test
    void shouldSkipWhenRequestContextMissing() {
        FeignHeaderInterceptor interceptor = noSourceCheck(List.of("X-Request-Id"));
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers()).isEmpty();
    }

    @Test
    void shouldDropIdentityHeadersWhenSourceUntrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "1");
        request.addHeader("X-Request-Id", "req-1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        FeignHeaderInterceptor interceptor = new FeignHeaderInterceptor(
            List.of("X-Request-Id", "X-User-Id"), List.of("X-User-Id"),
            "X-Gateway-Signed", "s3cret");
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        // 非身份头照常透传，身份头因来源不可信被丢弃
        assertThat(template.headers().get("X-Request-Id")).containsExactly("req-1");
        assertThat(template.headers()).doesNotContainKey("X-User-Id");
    }

    @Test
    void shouldFailFastWhenTrustedSourceRequiredButNotConfigured() {
        assertThatThrownBy(() -> new FeignHeaderInterceptor(
            List.of("X-User-Id"), List.of("X-User-Id"), "X-Gateway-Signed", "", true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("require-trusted-source");
    }

    @Test
    void shouldStartWhenTrustedSourceRequiredAndConfigured() {
        assertThat(new FeignHeaderInterceptor(
            List.of("X-User-Id"), List.of("X-User-Id"), "X-Gateway-Signed", "s3cret", true))
            .isNotNull();
    }

    @Test
    void shouldPropagateIdentityHeadersWhenSourceTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "1");
        request.addHeader("X-Gateway-Signed", "s3cret");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        FeignHeaderInterceptor interceptor = new FeignHeaderInterceptor(
            List.of("X-User-Id"), List.of("X-User-Id"), "X-Gateway-Signed", "s3cret");
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get("X-User-Id")).containsExactly("1");
    }

    // ------------------------------------------------------------------
    // 内部调用凭证携带（与 security 守卫衔接，GitHub issue #96）
    // ------------------------------------------------------------------

    @Test
    void shouldInjectInternalTokenEvenWithoutRequestContext() {
        // 定时任务/异步线程发起的调用没有入站请求上下文，但凭证必须照常携带（否则下游守卫 401）
        RequestContextHolder.resetRequestAttributes();
        FeignHeaderInterceptor interceptor = withInternalToken(List.of("X-Request-Id"), INTERNAL_HEADER,
            INTERNAL_TOKEN);
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get(INTERNAL_HEADER)).containsExactly(INTERNAL_TOKEN);
    }

    @Test
    void shouldInjectInternalTokenAlongsidePropagatedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "req-1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        FeignHeaderInterceptor interceptor = withInternalToken(List.of("X-Request-Id"), INTERNAL_HEADER,
            INTERNAL_TOKEN);
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get("X-Request-Id")).containsExactly("req-1");
        assertThat(template.headers().get(INTERNAL_HEADER)).containsExactly(INTERNAL_TOKEN);
    }

    @Test
    void shouldNotInjectInternalTokenWhenNotConfigured() {
        FeignHeaderInterceptor interceptor = withInternalToken(List.of(), INTERNAL_HEADER, "");
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey(INTERNAL_HEADER);
    }

    @Test
    void shouldNotOverrideExplicitlySetInternalTokenHeader() {
        FeignHeaderInterceptor interceptor = withInternalToken(List.of(), INTERNAL_HEADER, INTERNAL_TOKEN);
        RequestTemplate template = new RequestTemplate();
        // 模板已显式设置同名头（大小写不同），不得被覆盖
        template.header("x-internal-token", "template-value");

        interceptor.apply(template);

        assertThat(template.headers()).containsOnlyKeys("x-internal-token");
        assertThat(template.headers().get("x-internal-token")).containsExactly("template-value");
    }

    @Test
    void shouldUseCustomInternalTokenHeaderName() {
        FeignHeaderInterceptor interceptor = withInternalToken(List.of(), "X-Internal-Auth", INTERNAL_TOKEN);
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get("X-Internal-Auth")).containsExactly(INTERNAL_TOKEN);
        assertThat(template.headers()).doesNotContainKey(INTERNAL_HEADER);
    }

    @Test
    void shouldNotAppendInboundValueWhenInternalTokenHeaderIsAlsoPropagated() {
        // 兜底：若接入方把内部凭证头也配进 propagate-headers，入站伪造值不得被追加成第二个 header 值
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(INTERNAL_HEADER, "forged-by-client");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        FeignHeaderInterceptor interceptor = withInternalToken(List.of(INTERNAL_HEADER), INTERNAL_HEADER,
            INTERNAL_TOKEN);
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get(INTERNAL_HEADER)).containsExactly(INTERNAL_TOKEN);
    }

    @Test
    void shouldWarnAtStartupWhenInternalTokenMissing() {
        Logger logger = (Logger) LoggerFactory.getLogger(FeignHeaderInterceptor.class);
        ListAppender<ILoggingEvent> appender = attachAppender(logger);
        try {
            withInternalToken(List.of(), INTERNAL_HEADER, "");

            assertThat(appender.list)
                .as("未配置内部凭证必须在启动期告警（否则下游开启守卫后全部 401 且无人知道原因）")
                .anyMatch(event -> event.getLevel() == Level.WARN
                    && event.getFormattedMessage().contains("ypbin.cloud.feign.internal-token"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    /**
     * 安全红线：真实凭据绝不出现在日志或 {@code toString}。
     *
     * <p>做法：把 logger 调到 {@code TRACE} 以捕获全部事件（包括将来的 debug 日志），断言
     * 构造 + 注入全程<b>零日志</b>，并额外断言没有任何事件、任何 {@code toString} 含凭证值。</p>
     */
    @Test
    void shouldNeverExposeInternalTokenInLogsOrToString() {
        Logger logger = (Logger) LoggerFactory.getLogger(FeignHeaderInterceptor.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.TRACE);
        ListAppender<ILoggingEvent> appender = attachAppender(logger);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-Request-Id", "req-1");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            FeignHeaderInterceptor interceptor = withInternalToken(List.of("X-Request-Id"), INTERNAL_HEADER,
                INTERNAL_TOKEN);
            RequestTemplate template = new RequestTemplate();

            interceptor.apply(template);

            assertThat(template.headers().get(INTERNAL_HEADER)).containsExactly(INTERNAL_TOKEN);
            assertThat(interceptor.toString()).doesNotContain(INTERNAL_TOKEN);
            assertThat(appender.list)
                .as("凭证已配置时构造与注入全程不得产生任何日志，凭证自然无从经日志泄露")
                .isEmpty();
            assertThat(appender.list)
                .noneMatch(event -> event.getFormattedMessage().contains(INTERNAL_TOKEN));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }
    }
}

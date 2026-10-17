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

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ypbin.starter.security.internal.InternalTokenVerifier.Verdict;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * {@link InternalTokenVerifier} 安全不变量测试 —— 两种拒绝语义共用的校验核心。
 *
 * <p>覆盖：fail-closed（null/空/空白凭证一律拒绝）、凭证缺失与不匹配拒绝、正确凭证放行、
 * 自定义头名、空白头名回退默认、**日志不含凭证值**、失败日志的级别与"是否携带头"信息。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
class InternalTokenVerifierTest {

    private static final String SECRET = "s3cr3t-internal-token";
    private static final String HEADER = "X-Internal-Token";

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        appender = new ListAppender<>();
        appender.start();
        logger().addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger().detachAppender(appender);
    }

    private static Logger logger() {
        return (Logger) LoggerFactory.getLogger(InternalTokenVerifier.class);
    }

    private static MockHttpServletRequest request(String headerName, String headerValue) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/internal/devices");
        if (headerName != null) {
            request.addHeader(headerName, headerValue);
        }
        return request;
    }

    @Test
    @DisplayName("凭证正确：放行（MATCH）")
    void matchingTokenPasses() {
        InternalTokenVerifier verifier = new InternalTokenVerifier(SECRET, HEADER);
        assertThat(verifier.verify(request(HEADER, SECRET))).isEqualTo(Verdict.MATCH);
        assertThat(appender.list).isEmpty();
    }

    @Test
    @DisplayName("凭证不符：拒绝（MISMATCH），WARN 且不记录呈递值")
    void mismatchedTokenIsRejected() {
        InternalTokenVerifier verifier = new InternalTokenVerifier(SECRET, HEADER);

        assertThat(verifier.verify(request(HEADER, "wrong-value"))).isEqualTo(Verdict.MISMATCH);
        List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(messages.get(0)).contains("是否携带头=true");
        // 安全不变量：日志绝不出现呈递的凭证值
        assertThat(messages.get(0)).doesNotContain("wrong-value");
        assertThat(messages).noneMatch(message -> message.contains(SECRET));
    }

    @Test
    @DisplayName("未携带凭证头：拒绝（MISMATCH），日志标记未携带头且不含凭证")
    void missingHeaderIsRejected() {
        InternalTokenVerifier verifier = new InternalTokenVerifier(SECRET, HEADER);

        assertThat(verifier.verify(request(null, null))).isEqualTo(Verdict.MISMATCH);
        List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).contains("是否携带头=false");
        assertThat(messages).noneMatch(message -> message.contains(SECRET));
    }

    /** 未配置凭证的各种形态：null / 空串 / 纯空白。 */
    @Test
    @DisplayName("fail-closed：凭证未配置（null/空/空白）一律拒绝，且 ERROR 提示配置键")
    void unconfiguredTokenAlwaysRejects() {
        for (String unconfigured : new String[] {null, "", "   "}) {
            appender.list.clear();
            InternalTokenVerifier verifier = new InternalTokenVerifier(unconfigured, HEADER);

            // 即使呈递了"看起来正确"的头也必须拒绝：没有配置就没有信任边界
            assertThat(verifier.verify(request(HEADER, SECRET)))
                .as("未配置凭证（%s）时必须 fail-closed", unconfigured)
                .isEqualTo(Verdict.MISSING_CONFIG);
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.ERROR);
            assertThat(appender.list.get(0).getFormattedMessage())
                .contains(InternalTokenProperties.PREFIX + ".token");
        }
    }

    @Test
    @DisplayName("自定义头名生效；空白头名回退默认 X-Internal-Token")
    void headerNameIsConfigurableWithDefaultFallback() {
        InternalTokenVerifier custom = new InternalTokenVerifier(SECRET, "X-Custom-Internal");
        assertThat(custom.verify(request("X-Custom-Internal", SECRET))).isEqualTo(Verdict.MATCH);
        // 用默认头名呈递：自定义头名时读不到 ⇒ 拒绝
        assertThat(custom.verify(request(HEADER, SECRET))).isEqualTo(Verdict.MISMATCH);

        for (String blankHeader : new String[] {null, "", "  "}) {
            InternalTokenVerifier fallback = new InternalTokenVerifier(SECRET, blankHeader);
            assertThat(fallback.verify(request(HEADER, SECRET)))
                .as("空白头名（%s）必须回退默认头名，不得因读不到头而全量拒绝", blankHeader)
                .isEqualTo(Verdict.MATCH);
        }
    }

    @Test
    @DisplayName("比较按 UTF-8 字节进行：中文/emoji 凭证可正确匹配与拒绝")
    void comparisonIsUtf8ByteBased() {
        String unicodeSecret = "内部凭证-🔐";
        InternalTokenVerifier verifier = new InternalTokenVerifier(unicodeSecret, HEADER);

        assertThat(verifier.verify(request(HEADER, unicodeSecret))).isEqualTo(Verdict.MATCH);
        assertThat(verifier.verify(request(HEADER, unicodeSecret + "x"))).isEqualTo(Verdict.MISMATCH);
        assertThat(verifier.verify(request(HEADER, "内部凭证"))).isEqualTo(Verdict.MISMATCH);
    }

    @Test
    @DisplayName("前缀相同的凭证必须拒绝（不得因短前缀匹配而放行）")
    void prefixOfSecretIsRejected() {
        InternalTokenVerifier verifier = new InternalTokenVerifier(SECRET, HEADER);
        for (int length = 0; length < SECRET.length(); length++) {
            String prefix = SECRET.substring(0, length);
            assertThat(verifier.verify(request(HEADER, prefix)))
                .as("前缀 %d 位不得放行", length)
                .isEqualTo(Verdict.MISMATCH);
        }
    }

    @Test
    @DisplayName("固定文案不含任何输入值")
    void rejectionMessagesCarryNoInput() {
        assertThat(InternalTokenVerifier.messageOf(Verdict.MISSING_CONFIG))
            .contains(InternalTokenProperties.PREFIX + ".token")
            .doesNotContain(SECRET);
        assertThat(InternalTokenVerifier.messageOf(Verdict.MISMATCH))
            .isEqualTo("内部调用凭证校验失败");
    }
}

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
package cn.ypbin.starter.security.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * {@link IdentityLoginConflictWarner} 的检测与告警测试。
 *
 * <p><b>要防的是「静默」</b>：identity 模式下本地登录必然建不成会话，而历史上这件事
 * 既不让 CI 变红、也不打启动告警，直到用户登录失败（且错误口令反而 409）才暴露。
 * 本测试钉住：① 有 {@code /login} 映射时确实告警且文案含排障指引；② 没有时**不打扰**；
 * ③ 检测本身抛异常时**不打断启动**（只降级为一条 WARN）。</p>
 *
 * @author wenbin
 * @since 2026-10-20
 */
class IdentityLoginConflictWarnerTest {

    private static final String LOGGER_NAME = "cn.ypbin.starter.security.identity.IdentityLoginConflictWarner";

    private ListAppender<ILoggingEvent> appender;

    private Logger logger;

    @BeforeEach
    void setUp() {
        appender = new ListAppender<>();
        appender.start();
        logger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("★ 存在 /login 映射 ⇒ 启动告警，且文案含可照做的修法")
    void shouldWarnWhenLocalLoginEndpointPresent() {
        IdentityLoginConflictWarner warner = newWarner("/auth/login", "/auth/codes");

        assertThat(warner.detectLoginPatterns()).containsExactly("/auth/login");

        warner.afterSingletonsInstantiated();

        assertThat(warnMessages())
            .as("必须告警——这正是 2026-10-20 事故里缺失的那条提示")
            .anySatisfy(message -> assertThat(message)
                .contains("/auth/login")
                .contains("ypbin.security.identity.enabled=false"));
    }

    @Test
    @DisplayName("没有 /login 映射（正常下游服务）⇒ 不告警，避免噪音")
    void shouldNotWarnWithoutLocalLoginEndpoint() {
        IdentityLoginConflictWarner warner = newWarner("/system/user/list", "/iot/devices");

        assertThat(warner.detectLoginPatterns()).isEmpty();
        warner.afterSingletonsInstantiated();

        assertThat(warnMessages()).isEmpty();
    }

    @Test
    @DisplayName("检测自身抛异常 ⇒ 不打断启动，只留一条带堆栈的 WARN（禁静默降级）")
    void detectionFailureShouldNotBreakStartup() {
        RequestMappingHandlerMapping broken = mock(RequestMappingHandlerMapping.class);
        when(broken.getHandlerMethods()).thenThrow(new IllegalStateException("boom"));
        IdentityLoginConflictWarner warner = new IdentityLoginConflictWarner(
            providerOf(broken));

        // 不抛：旁路诊断绝不能把服务搞挂
        warner.afterSingletonsInstantiated();

        assertThat(warnMessages())
            .anySatisfy(message -> assertThat(message).contains("冲突检查失败"));
        assertThat(appender.list)
            .as("必须留完整堆栈（log.warn(..., ex)），不能吞异常")
            .anySatisfy(event -> assertThat(event.getThrowableProxy()).isNotNull());
    }

    @Test
    @DisplayName("容器里没有 RequestMappingHandlerMapping（非 Web MVC 宿主）⇒ 检测为空、不报错")
    void shouldTolerateMissingHandlerMapping() {
        IdentityLoginConflictWarner warner = new IdentityLoginConflictWarner(providerOf());

        assertThat(warner.detectLoginPatterns()).isEmpty();
        warner.afterSingletonsInstantiated();
        assertThat(warnMessages()).isEmpty();
    }

    /** 构造带指定路径映射的告警器。 */
    private static IdentityLoginConflictWarner newWarner(String... patterns) {
        Map<RequestMappingInfo, HandlerMethod> handlers = new LinkedHashMap<>();
        for (String pattern : patterns) {
            RequestMappingInfo info = mock(RequestMappingInfo.class);
            when(info.getPatternValues()).thenReturn(java.util.Set.of(pattern));
            handlers.put(info, mock(HandlerMethod.class));
        }
        RequestMappingHandlerMapping mapping = mock(RequestMappingHandlerMapping.class);
        when(mapping.getHandlerMethods()).thenReturn(handlers);
        return new IdentityLoginConflictWarner(providerOf(mapping));
    }

    /** 固定数量的 ObjectProvider 桩（只实现本类用到的 orderedStream）。 */
    @SafeVarargs
    private static ObjectProvider<RequestMappingHandlerMapping> providerOf(
            RequestMappingHandlerMapping... mappings) {
        return new ObjectProvider<>() {
            @Override
            public java.util.stream.Stream<RequestMappingHandlerMapping> orderedStream() {
                return java.util.Arrays.stream(mappings);
            }

            @Override
            public RequestMappingHandlerMapping getObject(Object... args) {
                throw new UnsupportedOperationException();
            }

            @Override
            public RequestMappingHandlerMapping getIfAvailable() {
                throw new UnsupportedOperationException();
            }

            @Override
            public RequestMappingHandlerMapping getIfUnique() {
                throw new UnsupportedOperationException();
            }

            @Override
            public RequestMappingHandlerMapping getObject() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /** 取出本次测试期间记录的 WARN 文本。 */
    private java.util.List<String> warnMessages() {
        java.util.List<String> messages = new java.util.ArrayList<>();
        for (ILoggingEvent event : appender.list) {
            if (event.getLevel() == Level.WARN) {
                messages.add(event.getFormattedMessage());
            }
        }
        return messages;
    }
}

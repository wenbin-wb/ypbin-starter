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
package cn.ypbin.starter.security.handler;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.SaTokenException;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * {@link SaTokenExceptionHandler} 测试：既覆盖鉴权异常的响应码/文案映射，也把「日志刻意不带
 * {@link Throwable}」的降噪约定钉住（理由见该处理器 Javadoc）。
 *
 * <p>这些用例失败时的正确处置不是直接给日志补堆栈，而是先论证「该类拒绝已不再是常态流量」——
 * 未登录/无权限由前端 token 过期与爬虫穷举高频触发，打印堆栈只会放大日志量并淹没真实故障告警。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
class SaTokenExceptionHandlerTest {

    private final SaTokenExceptionHandler handler = new SaTokenExceptionHandler();

    @Test
    void notLoginShouldMapUnauthorizedAndLogWithoutThrowable() {
        NotLoginException failure = new NotLoginException("token 无效：xxx", "login-type", NotLoginException.INVALID_TOKEN);

        List<ILoggingEvent> events = captureLogs(() -> {
            R<Void> result = handler.handleNotLogin(failure);
            assertThat(result.getCode()).isEqualTo(GlobalErrorCode.UNAUTHORIZED.getCode());
        });

        ILoggingEvent event = singleWarn(events);
        assertThat(event.getFormattedMessage())
            .contains(NotLoginException.INVALID_TOKEN)
            .contains("token 无效");
        assertThat(event.getThrowableProxy())
            .as("预期内的鉴权结果，刻意不打堆栈以降噪；如需堆栈请先论证拒绝量可控")
            .isNull();
    }

    @Test
    void saTokenFallbackShouldMapForbiddenAndLogWithoutThrowable() {
        SaTokenException failure = new SaTokenException("未能查询到对应终端信息，无法续期");

        List<ILoggingEvent> events = captureLogs(() -> {
            R<Void> result = handler.handleSaTokenException(failure);
            assertThat(result.getCode()).isEqualTo(GlobalErrorCode.FORBIDDEN.getCode());
        });

        ILoggingEvent event = singleWarn(events);
        assertThat(event.getFormattedMessage())
            .contains(SaTokenException.class.getSimpleName())
            .contains("无法续期");
        assertThat(event.getThrowableProxy()).as("能力边界而非故障，按降噪约定不打堆栈").isNull();
    }

    /** 在 {@link SaTokenExceptionHandler} logger 上挂 {@link ListAppender} 捕获动作期间的全部日志事件 */
    private static List<ILoggingEvent> captureLogs(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(SaTokenExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
        }
        return List.copyOf(appender.list);
    }

    private static ILoggingEvent singleWarn(List<ILoggingEvent> events) {
        return events.stream()
            .filter(event -> event.getLevel() == Level.WARN)
            .findFirst()
            .orElseThrow(() -> new AssertionError("未捕获到 WARN 级日志：" + events));
    }
}

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
package cn.ypbin.starter.gateway.handler;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ypbin.starter.core.exception.GlobalErrorCode;
import cn.ypbin.starter.core.model.R;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link GatewayExceptionHandler} 单元测试。
 *
 * <p>日志断言部分钉住本类的「带堆栈 / 不带堆栈」约定：非 404 的网关异常（5xx、401/403/400）必须把
 * {@link Throwable} 传给 logger 才能定位抛出来源；404 属未知路由常态流量，刻意只记 message 以降噪
 * （见 {@link GatewayExceptionHandler#handle} 内注释），避免后人误判为遗漏而机械补上堆栈。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
class GatewayExceptionHandlerTest {

    private GatewayExceptionHandler handler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new GatewayExceptionHandler(objectMapper);
    }

    @Test
    void shouldResolveNotFoundFor404() {
        R<Void> result = handler.resolveBody(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThat(result.getCode()).isEqualTo(GlobalErrorCode.NOT_FOUND.getCode());
    }

    @Test
    void shouldResolveUnauthorizedFor401() {
        R<Void> result = handler.resolveBody(new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        assertThat(result.getCode()).isEqualTo(GlobalErrorCode.UNAUTHORIZED.getCode());
    }

    @Test
    void shouldResolveInternalErrorForUnknown() {
        R<Void> result = handler.resolveBody(new RuntimeException("bomb"));

        assertThat(result.getCode()).isEqualTo(GlobalErrorCode.INTERNAL_ERROR.getCode());
    }

    @Test
    void shouldReturnHttp200ForNotFound() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/not-found"));
        handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldPassThrowableToLoggerForServerError() {
        RuntimeException failure = new RuntimeException("bomb");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/boom"));

        List<ILoggingEvent> events = captureLogs(() -> handler.handle(exchange, failure).block());

        ILoggingEvent event = singleEvent(events, Level.ERROR);
        assertThat(event.getThrowableProxy()).as("5xx 网关异常必须带 Throwable 才能打印栈").isNotNull();
        assertThat(event.getThrowableProxy().getClassName()).isEqualTo(RuntimeException.class.getName());
        assertThat(event.getFormattedMessage()).contains("/boom");
    }

    @Test
    void shouldPassThrowableToLoggerForClientErrorThatIsNotNotFound() {
        // 回归护栏：修复前该分支写 `log.warn("{} -> {}", uri, ex.getMessage())`，401/403/400 的抛出点
        // （鉴权过滤器、路由谓词、参数解析器）只凭 message 无法定位
        ResponseStatusException failure = new ResponseStatusException(HttpStatus.FORBIDDEN, "no pass");
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/forbidden"));

        List<ILoggingEvent> events = captureLogs(() -> handler.handle(exchange, failure).block());

        ILoggingEvent event = singleEvent(events, Level.WARN);
        assertThat(event.getThrowableProxy())
            .as("非 404 的客户端侧异常必须把异常对象传给 logger，否则只剩 message")
            .isNotNull();
        assertThat(event.getThrowableProxy().getClassName()).isEqualTo(ResponseStatusException.class.getName());
        assertThat(event.getFormattedMessage()).contains("/forbidden");
    }

    @Test
    void shouldNotPassThrowableForNotFoundNoise() {
        // 既定降噪约定：404 只记 message。若后人把此处改成传 Throwable，本用例失败即为提醒——
        // 需要的是先论证「404 已不再是常态流量」，而不是直接加堆栈
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/not-found"));

        List<ILoggingEvent> events = captureLogs(
            () -> handler.handle(exchange, new ResponseStatusException(HttpStatus.NOT_FOUND)).block());

        ILoggingEvent event = singleEvent(events, Level.WARN);
        assertThat(event.getThrowableProxy()).as("404 刻意不打堆栈以降噪，属既定约定").isNull();
        assertThat(event.getFormattedMessage()).contains("/not-found");
    }

    /** 在 {@link GatewayExceptionHandler} logger 上挂 {@link ListAppender} 捕获动作期间的全部日志事件 */
    private static List<ILoggingEvent> captureLogs(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(GatewayExceptionHandler.class);
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

    private static ILoggingEvent singleEvent(List<ILoggingEvent> events, Level level) {
        return events.stream()
            .filter(event -> event.getLevel() == level)
            .findFirst()
            .orElseThrow(() -> new AssertionError("未捕获到 " + level + " 级日志：" + events));
    }
}

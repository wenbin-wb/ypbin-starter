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
package cn.ypbin.starter.log.aspect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ypbin.starter.log.annotation.Log;
import cn.ypbin.starter.log.enums.Include;
import cn.ypbin.starter.log.support.LogCollector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * {@link LogAspect} 采集失败时的行为与日志测试。
 *
 * <p>断言两点：① 采集链路抛 {@code Exception}/{@code Error} 都不影响业务方法的返回值与业务异常；
 * ② 告警日志带完整堆栈（可诊断），而不是只打 message——后者正是导致"操作日志为空却查不到原因"的根因。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class LogAspectCollectFailureTest {

    /** 采集失败可控的 mock 采集器：跨用例复用，逐用例 reset。 */
    private static final LogCollector COLLECTOR = mock(LogCollector.class);

    private ListAppender<ILoggingEvent> appender;
    private AnnotationConfigApplicationContext ctx;
    private DemoService service;

    @BeforeEach
    void setUp() {
        reset(COLLECTOR);
        appender = new ListAppender<>();
        appender.start();
        logger().addAppender(appender);
        ctx = new AnnotationConfigApplicationContext(Config.class);
        service = ctx.getBean(DemoService.class);
    }

    @AfterEach
    void tearDown() {
        logger().detachAppender(appender);
        if (ctx != null) {
            ctx.close();
        }
    }

    private static Logger logger() {
        return (Logger) LoggerFactory.getLogger(LogAspect.class);
    }

    /** 采集抛 RuntimeException：业务方法正常返回，且留下带堆栈的 WARN。 */
    @Test
    void collectThrowsException_businessUnaffected_andWarnsWithStacktrace() {
        doThrow(new IllegalStateException("collector down")).when(COLLECTOR)
            .collect(any(), any(), any(), any(), any());

        assertThat(service.create()).isEqualTo("created");

        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("操作日志未记录").contains("collector down");
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getThrowableProxy().getClassName()).isEqualTo(IllegalStateException.class.getName());
            // 关键断言：必须带堆栈；只打 message 时这里是空的
            assertThat(event.getThrowableProxy().getStackTraceElementProxyArray()).isNotEmpty();
        });
    }

    /**
     * 采集抛 Error（非 Exception）：不得顶替业务原本的异常。
     *
     * <p>该 catch 位于 {@code finally} 中，若放行 Error，它会覆盖 {@code point.proceed()} 抛出的业务异常，
     * 使用者将看到 {@code NoClassDefFoundError} 而非真实业务错误。</p>
     */
    @Test
    void collectThrowsError_doesNotMaskBusinessException() {
        doThrow(new NoClassDefFoundError("host provider broken")).when(COLLECTOR)
            .collect(any(), any(), any(), any(), any());

        assertThatThrownBy(() -> service.fail())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("boom");

        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("操作日志未记录");
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getThrowableProxy().getClassName()).isEqualTo(NoClassDefFoundError.class.getName());
            assertThat(event.getThrowableProxy().getStackTraceElementProxyArray()).isNotEmpty();
        });
    }

    @EnableAspectJAutoProxy
    static class Config {

        @Bean
        LogCollector logCollector() {
            return COLLECTOR;
        }

        @Bean
        LogAspect logAspect(LogCollector collector, ApplicationEventPublisher publisher) {
            return new LogAspect(collector, publisher, Include.defaultIncludes());
        }

        @Bean
        DemoService demoService() {
            return new DemoService();
        }
    }

    @Log(module = "演示")
    static class DemoService {

        @Log("创建")
        public String create() {
            return "created";
        }

        @Log("失败")
        public void fail() {
            throw new IllegalStateException("boom");
        }
    }
}

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
package cn.ypbin.starter.log.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ypbin.starter.log.dao.LogDao;
import cn.ypbin.starter.log.model.LogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * {@link LogEventListener} 落库失败时的日志测试。
 *
 * <p>落库失败是"操作日志查库为空"的直接原因，必须留下带完整堆栈与失败实现类名的告警，
 * 否则使用者只能看到"没有日志"这一现象、拿不到任何可诊断信息。</p>
 *
 * @author wenbin
 * @since 2026-09-16
 */
class LogEventListenerTest {

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        logger().addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger().detachAppender(appender);
    }

    private static Logger logger() {
        return (Logger) LoggerFactory.getLogger(LogEventListener.class);
    }

    @Test
    void persistFailure_warnsWithStacktraceAndDaoImplementation() {
        LogDao logDao = mock(LogDao.class);
        doThrow(new IllegalStateException("db down")).when(logDao).add(any());

        // 未走 @Async 代理，直接同步调用即可断言日志
        new LogEventListener(logDao).onLogEvent(new LogEvent(new LogRecord()));

        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                .contains("操作日志未落库")
                .contains("db down");
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getThrowableProxy().getClassName()).isEqualTo(IllegalStateException.class.getName());
            assertThat(event.getThrowableProxy().getStackTraceElementProxyArray()).isNotEmpty();
        });
    }
}

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
package cn.ypbin.starter.job.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;

/**
 * {@link JobManager} 执行失败日志的回归测试：必须把 {@link Throwable} 作为日志参数传给 SLF4J
 * 以打印完整堆栈（issue #95）。
 *
 * <p><b>为何专门测日志</b>：原实现写 {@code "err={}", e.getMessage()}，当异常 message 为 {@code null}
 * （典型是 {@link NullPointerException}）时日志只剩 {@code err=null}，堆栈与异常类型全部丢失，
 * 线上无法定位。仅断言 {@code listener.onError} 收到异常并不能守住这一退化——它本来就收得到。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
class JobManagerLoggingTest {

    @Test
    @DisplayName("★ 执行失败日志必须携带 Throwable（NPE 的 message=null 也要有堆栈）")
    void executionFailureLogCarriesThrowable() {
        NullPointerException failure = new NullPointerException();
        AtomicReference<Throwable> reported = new AtomicReference<>();
        JobExecutionListener listener = new JobExecutionListener() {
            @Override
            public void onError(JobContext context, long durationMillis, Throwable error) {
                reported.set(error);
            }
        };
        JobManager manager = new JobManager("node-1",
            mock(TaskScheduler.class),
            Executors.newVirtualThreadPerTaskExecutor(),
            Map.of("boom", ctx -> {
                throw failure;
            }),
            listener,
            new JobManager.JobLock() {
                @Override
                public boolean tryLock(String key, String owner, Duration ttl) {
                    return true;
                }

                @Override
                public boolean unlock(String key, String owner) {
                    return true;
                }
            },
            new SpringCronService());

        JobDefinition definition = new JobDefinition();
        definition.setId(1L);
        definition.setName("boom-job");
        definition.setExecutor("boom");
        definition.setFixedRateSeconds(3600L);

        Logger logger = (Logger) LoggerFactory.getLogger(JobManager.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            manager.triggerNow(definition);
            await().atMost(Duration.ofSeconds(5)).until(() -> reported.get() != null);
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(reported.get()).isSameAs(failure);

        ILoggingEvent event = appender.list.stream()
            .filter(e -> e.getLevel() == Level.WARN)
            .filter(e -> e.getFormattedMessage().contains("job execute failed"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("未记录 job 执行失败的 WARN 日志"));
        assertThat(event.getThrowableProxy())
            .as("日志必须携带 Throwable 才能打印堆栈；缺失即回退成 message-only（NPE 时只剩 err=null）")
            .isNotNull();
        assertThat(event.getThrowableProxy().getClassName())
            .isEqualTo(NullPointerException.class.getName());
        assertThat(event.getFormattedMessage())
            .contains("name=boom-job")
            .doesNotContain("err=null");
    }
}

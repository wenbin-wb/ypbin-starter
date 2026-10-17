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
package cn.ypbin.starter.cache.multilevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * {@link SafeCacheMetricsRecorder} 的隔离语义测试。
 *
 * <p>三类证据合起来才算证明「埋点不影响主流程」：① 六个埋点方法<strong>逐个</strong>真的委托给
 * delegate（防漏接线的空实现，即「异常被吞掉了、但指标也没记」）；② delegate 抛
 * {@link RuntimeException} 时六个方法都不外抛，且每个异常都以 ERROR + <strong>完整堆栈</strong> +
 * 被调用方法名记录（不是静默降级）；③ {@link Error} 原样上抛（JVM 级故障不得被当埋点小故障吞掉）。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
class SafeCacheMetricsRecorderTest {

    /** 逐次记录调用方法名的 delegate 替身。 */
    private static final class RecordingRecorder implements CacheMetricsRecorder {

        private final List<String> calls = new ArrayList<>();

        @Override
        public void l1Hit() {
            calls.add("l1Hit");
        }

        @Override
        public void l1Miss() {
            calls.add("l1Miss");
        }

        @Override
        public void l2Hit() {
            calls.add("l2Hit");
        }

        @Override
        public void l2Miss() {
            calls.add("l2Miss");
        }

        @Override
        public void nullSentinelHit() {
            calls.add("nullSentinelHit");
        }

        @Override
        public void load() {
            calls.add("load");
        }
    }

    /** 每次调用都抛 {@link RuntimeException}（模拟观测后端故障/宿主实现 bug）。 */
    private static final class ThrowingRecorder implements CacheMetricsRecorder {

        private final AtomicInteger attempts = new AtomicInteger();

        private IllegalStateException failure(String method) {
            attempts.incrementAndGet();
            return new IllegalStateException("metrics-backend-down:" + method);
        }

        @Override
        public void l1Hit() {
            throw failure("l1Hit");
        }

        @Override
        public void l1Miss() {
            throw failure("l1Miss");
        }

        @Override
        public void l2Hit() {
            throw failure("l2Hit");
        }

        @Override
        public void l2Miss() {
            throw failure("l2Miss");
        }

        @Override
        public void nullSentinelHit() {
            throw failure("nullSentinelHit");
        }

        @Override
        public void load() {
            throw failure("load");
        }
    }

    /** JVM 级故障（{@link Error}）替身：必须原样上抛，不得被当作埋点故障吞掉。 */
    private static final class JvmLevelError extends Error {

        private JvmLevelError(String message) {
            super(message);
        }
    }

    /** 只让 {@code l1Hit} 抛 {@link Error} 的 delegate；其余方法为空实现。 */
    private static final class ErrorRecorder implements CacheMetricsRecorder {

        @Override
        public void l1Hit() {
            throw new JvmLevelError("out-of-memory-like");
        }

        @Override
        public void l1Miss() {
        }

        @Override
        public void l2Hit() {
        }

        @Override
        public void l2Miss() {
        }

        @Override
        public void nullSentinelHit() {
        }

        @Override
        public void load() {
        }
    }

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
        return (Logger) LoggerFactory.getLogger(SafeCacheMetricsRecorder.class);
    }

    private List<ILoggingEvent> errors() {
        return appender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
    }

    @Test
    @DisplayName("六个埋点方法全部委托给 delegate（不允许出现漏接线的空实现）")
    void everyMethodDelegates() {
        RecordingRecorder delegate = new RecordingRecorder();
        SafeCacheMetricsRecorder safe = new SafeCacheMetricsRecorder(delegate);

        safe.l1Hit();
        safe.l1Miss();
        safe.l2Hit();
        safe.l2Miss();
        safe.nullSentinelHit();
        safe.load();

        assertThat(delegate.calls).containsExactly(
            "l1Hit", "l1Miss", "l2Hit", "l2Miss", "nullSentinelHit", "load");
        assertThat(errors()).as("正常委托不应产生任何 ERROR").isEmpty();
    }

    @Test
    @DisplayName("delegate 抛 RuntimeException：六个方法一律隔离不外抛，且每个都记 ERROR + 完整堆栈 + 方法名")
    void runtimeExceptionIsIsolatedAndLoggedWithStack() {
        ThrowingRecorder delegate = new ThrowingRecorder();
        SafeCacheMetricsRecorder safe = new SafeCacheMetricsRecorder(delegate);

        assertThatCode(() -> {
            safe.l1Hit();
            safe.l1Miss();
            safe.l2Hit();
            safe.l2Miss();
            safe.nullSentinelHit();
            safe.load();
        }).doesNotThrowAnyException();

        assertThat(delegate.attempts).as("埋点必须真的被调用过（不是整段埋点被静默跳过）").hasValue(6);
        assertThat(errors()).as("每次被隔离的异常都必须有 ERROR 记录").hasSize(6);
        assertThat(errors()).allSatisfy(event -> {
            assertThat(event.getFormattedMessage())
                .as("日志必须指出是哪个埋点方法出的问题")
                .contains("method=");
            assertThat(event.getThrowableProxy())
                .as("观测后端故障必须留下完整堆栈，而不是只记一行文案")
                .isNotNull();
            assertThat(event.getThrowableProxy().getClassName()).isEqualTo(IllegalStateException.class.getName());
        });
        assertThat(errors()).extracting(ILoggingEvent::getFormattedMessage)
            .anySatisfy(message -> assertThat(message).contains("l1Hit"))
            .anySatisfy(message -> assertThat(message).contains("load"));
    }

    @Test
    @DisplayName("delegate 抛 Error：原样上抛（JVM 级故障不得被当作埋点故障吞掉）")
    void errorIsPropagated() {
        SafeCacheMetricsRecorder safe = new SafeCacheMetricsRecorder(new ErrorRecorder());

        assertThatThrownBy(safe::l1Hit)
            .as("Error 必须原样上抛，否则会掩盖真正的进程级问题")
            .isInstanceOf(JvmLevelError.class)
            .hasMessage("out-of-memory-like");
        assertThat(errors()).as("Error 不是被隔离的埋点异常，不应产生隔离 ERROR 日志").isEmpty();
    }
}

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
package cn.ypbin.starter.tools.warn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * {@link KeyedWarnThrottled} 的按键分桶语义测试。
 *
 * <p><b>核心要钉住的是「分桶不串味」</b>：一台设备刷屏不得抑制其它设备的告警——那等于静默
 * 丢失其余故障。这正是不用 {@link WarnThrottled}（单一窗口）而需要分桶的理由。</p>
 *
 * <p>时序用可注入纳秒时钟而非 {@code Thread.sleep}：跨间隔语义必须确定性可验。</p>
 *
 * @author wenbin
 * @since 2026-10-19
 */
class KeyedWarnThrottledTest {

    private static final String LOGGER_NAME = "cn.ypbin.starter.tools.warn.KeyedWarnThrottledTest";

    private static final long ONE_SECOND_NANOS = 1_000_000_000L;

    /** 可注入纳秒时钟起点（非 0，便于验证「next=0 表示从未输出」的哨兵语义）。 */
    private final AtomicLong nanoTime = new AtomicLong(ONE_SECOND_NANOS);

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

    private KeyedWarnThrottled throttled(long intervalMillis) {
        return new KeyedWarnThrottled(intervalMillis, logger, nanoTime::get);
    }

    private List<String> warnMessages() {
        List<String> messages = new ArrayList<>();
        for (ILoggingEvent event : appender.list) {
            if (event.getLevel() == Level.WARN) {
                messages.add(event.getFormattedMessage());
            }
        }
        return messages;
    }

    @Test
    @DisplayName("同一 key：间隔内多次只输出 1 条，跨间隔再输出")
    void sameKeyShouldThrottleWithinInterval() {
        KeyedWarnThrottled throttled = throttled(1_000);

        assertThat(throttled.tryWarn("dev-1", "warn 1")).isTrue();
        assertThat(throttled.tryWarn("dev-1", "warn 2")).isFalse();
        assertThat(throttled.tryWarn("dev-1", "warn 3")).isFalse();
        assertThat(warnMessages()).containsExactly("warn 1");

        nanoTime.addAndGet(1_001L * 1_000_000L);
        assertThat(throttled.tryWarn("dev-1", "warn 4")).isTrue();
        assertThat(warnMessages()).containsExactly("warn 1", "warn 4");
    }

    @Test
    @DisplayName("★ 分桶隔离：一个 key 刷屏不得抑制另一个 key 的告警")
    void differentKeysMustNotShareWindow() {
        KeyedWarnThrottled throttled = throttled(60_000);

        assertThat(throttled.tryWarn("dev-1", "dev-1 first")).isTrue();
        for (int i = 0; i < 100; i++) {
            assertThat(throttled.tryWarn("dev-1", "dev-1 spam")).isFalse();
        }

        assertThat(throttled.tryWarn("dev-2", "dev-2 first"))
            .as("分桶隔离：dev-1 的刷屏不得抑制 dev-2（否则等于静默丢失其它设备的故障）")
            .isTrue();
        assertThat(warnMessages()).containsExactly("dev-1 first", "dev-2 first");
        assertThat(throttled.windowCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("acquire：放行时返回此前被抑制条数，被抑制时返回 -1")
    void acquireShouldReportSuppressedCount() {
        KeyedWarnThrottled throttled = throttled(1_000);

        assertThat(throttled.acquire("p1")).as("首次放行，此前抑制数 0").isZero();
        assertThat(throttled.acquire("p1")).isEqualTo(-1L);
        assertThat(throttled.acquire("p1")).isEqualTo(-1L);

        nanoTime.addAndGet(1_001L * 1_000_000L);
        assertThat(throttled.acquire("p1")).as("放行时返回此前被抑制的 2 条").isEqualTo(2L);
        assertThat(throttled.acquire("p1")).isEqualTo(-1L);
        nanoTime.addAndGet(1_001L * 1_000_000L);
        assertThat(throttled.acquire("p1")).as("上一轮只抑制 1 条，且已清零").isEqualTo(1L);
    }

    @Test
    @DisplayName("累计抑制计数单调递增、不因放行清零")
    void suppressedCountShouldBeMonotonic() {
        KeyedWarnThrottled throttled = throttled(1_000);

        throttled.tryWarn("k", "first");
        throttled.tryWarn("k", "suppressed 1");
        throttled.tryWarn("k", "suppressed 2");
        assertThat(throttled.suppressedCount("k")).isEqualTo(2L);

        nanoTime.addAndGet(1_001L * 1_000_000L);
        throttled.tryWarn("k", "after window");
        assertThat(throttled.suppressedCount("k")).as("放行不清零累计值").isEqualTo(2L);
        assertThat(throttled.suppressedCount("never-seen")).isZero();
    }

    @Test
    @DisplayName("间隔 0 不节流；负间隔构造即失败（不静默放行）")
    void zeroIntervalMeansNoThrottleAndNegativeRejected() {
        KeyedWarnThrottled notThrottled = throttled(0);
        assertThat(notThrottled.tryWarn("k", "1")).isTrue();
        assertThat(notThrottled.tryWarn("k", "2")).isTrue();
        assertThat(warnMessages()).containsExactly("1", "2");

        assertThatThrownBy(() -> new KeyedWarnThrottled(-1, logger))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不能为负数");
    }

    @Test
    @DisplayName("null 参数快速失败（不静默吞）")
    void nullArgumentsShouldFailFast() {
        KeyedWarnThrottled throttled = throttled(1_000);
        assertThatThrownBy(() -> throttled.tryWarn(null, "x")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> throttled.tryWarn("k", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> throttled.acquire(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("并发安全：20 线程 × 50 次同 key，只放行 1 条且抑制计数一致")
    void concurrentCallsShouldBeSafe() throws Exception {
        KeyedWarnThrottled throttled = throttled(60_000);
        int threads = 20;
        int perThread = 50;
        List<Thread> pool = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            Thread thread = new Thread(() -> {
                for (int i = 0; i < perThread; i++) {
                    throttled.tryWarn("shared", "spam");
                }
            });
            pool.add(thread);
            thread.start();
        }
        for (Thread thread : pool) {
            thread.join();
        }
        int total = threads * perThread;
        assertThat(warnMessages()).as("60s 窗口内只应放行 1 条").hasSize(1);
        assertThat(throttled.suppressedCount("shared")).isEqualTo(total - 1L);
    }
}

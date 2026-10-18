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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * {@link WarnThrottled} 单元测试。
 *
 * <p>时序用可注入时钟（{@link MutableClock}）而非 {@code Thread.sleep}：跨间隔语义必须确定性可验，
 * 靠睡眠等真实时间既慢又可能因调度抖动偶发失败。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class WarnThrottledTest {

    private static final String LOGGER_NAME = "cn.ypbin.starter.tools.warn.WarnThrottledTest";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-18T00:00:00Z"));

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

    private WarnThrottled throttled(long intervalMillis) {
        return new WarnThrottled(intervalMillis, logger, clock);
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
    @DisplayName("间隔内多次调用只打 1 条，其余被抑制并计数")
    void shouldWarnOnlyOnceWithinInterval() {
        WarnThrottled throttled = throttled(1_000);

        assertThat(throttled.tryWarn("队列已满，已丢弃 {} 条", 1)).isTrue();
        assertThat(throttled.tryWarn("队列已满，已丢弃 {} 条", 2)).isFalse();
        assertThat(throttled.tryWarn("队列已满，已丢弃 {} 条", 3)).isFalse();

        assertThat(warnMessages()).containsExactly("队列已满，已丢弃 1 条");
        assertThat(throttled.suppressedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("跨过间隔后再次放行，抑制计数继续累加")
    void shouldWarnAgainAfterInterval() {
        WarnThrottled throttled = throttled(1_000);
        assertThat(throttled.tryWarn("第一次")).isTrue();

        // 未到间隔：抑制
        clock.advanceMillis(999);
        assertThat(throttled.tryWarn("未到间隔")).isFalse();
        assertThat(throttled.suppressedCount()).isEqualTo(1);

        // 到间隔：放行，且抑制计数不清零（用于 WARN 里带「本轮已抑制 N 条」）
        clock.advanceMillis(1);
        assertThat(throttled.tryWarn("到间隔")).isTrue();
        assertThat(throttled.suppressedCount()).isEqualTo(1);

        assertThat(warnMessages()).containsExactly("第一次", "到间隔");
    }

    @Test
    @DisplayName("间隔为 0 表示不节流：每次放行、不产生抑制")
    void zeroIntervalShouldNeverSuppress() {
        WarnThrottled throttled = throttled(0);

        assertThat(throttled.tryWarn("a")).isTrue();
        assertThat(throttled.tryWarn("b")).isTrue();
        assertThat(throttled.tryWarn("c")).isTrue();
        assertThat(throttled.suppressedCount()).isZero();
        assertThat(throttled.minIntervalMillis()).isZero();
        assertThat(warnMessages()).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("负数间隔属配置错误，构造即失败（不静默当作 0）")
    void negativeIntervalShouldFailFast() {
        assertThatThrownBy(() -> new WarnThrottled(-1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("minIntervalMillis");
    }

    @Test
    @DisplayName("format 为 null 拒绝；日志器为 null 拒绝")
    void nullArgumentsShouldFailFast() {
        WarnThrottled throttled = throttled(1_000);
        assertThatThrownBy(() -> throttled.tryWarn(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("format");
        assertThatThrownBy(() -> new WarnThrottled(1_000, null, clock))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("时钟回拨：立即放行而不是静默等到追平")
    void clockRollbackShouldNotSuppressForever() {
        WarnThrottled throttled = throttled(1_000);
        assertThat(throttled.tryWarn("首次")).isTrue();

        clock.setInstant(clock.instant().minusSeconds(60));
        assertThat(throttled.tryWarn("时钟回拨后")).isTrue();
        assertThat(warnMessages()).containsExactly("首次", "时钟回拨后");
    }

    @Test
    @DisplayName("并发：多线程抢同一间隔，只有 1 条被放行，其余全部计入抑制")
    void shouldBeThreadSafe() throws Exception {
        WarnThrottled throttled = new WarnThrottled(60_000, logger, Clock.systemUTC());
        int threads = 16;
        int callsPerThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger passed = new AtomicInteger();
        try {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    try {
                        start.await();
                        for (int j = 0; j < callsPerThread; j++) {
                            if (throttled.tryWarn("并发告警")) {
                                passed.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).as("并发用例超时").isTrue();
        } finally {
            pool.shutdownNow();
        }

        int total = threads * callsPerThread;
        // 真实时钟下可能因执行时间跨过 60s 间隔而多放行，但绝对不可能超过「每次放行 = 一个明显更晚的时刻」
        assertThat(passed.get()).isBetween(1, 2);
        assertThat(throttled.suppressedCount()).isEqualTo(total - passed.get());
        assertThat(warnMessages()).hasSize(passed.get());
    }

    /** 可推进/回拨的测试时钟。 */
    private static final class MutableClock extends Clock {

        private final AtomicLong millis;

        private MutableClock(Instant start) {
            this.millis = new AtomicLong(start.toEpochMilli());
        }

        private void advanceMillis(long delta) {
            millis.addAndGet(delta);
        }

        private void setInstant(Instant instant) {
            millis.set(instant.toEpochMilli());
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException("测试时钟不支持切换时区");
        }

        @Override
        public long millis() {
            return millis.get();
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
        }
    }
}

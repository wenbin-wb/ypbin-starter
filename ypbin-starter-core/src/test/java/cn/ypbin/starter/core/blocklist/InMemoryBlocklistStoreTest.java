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
package cn.ypbin.starter.core.blocklist;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 进程内黑名单存储用例。
 *
 * @author wenbin
 * @since 2026-11-04
 */
class InMemoryBlocklistStoreTest {

    private static final Instant START = Instant.parse("2026-11-04T00:00:00Z");

    @Test
    @DisplayName("规则全量替换后立即可见")
    void replaceRules() {
        InMemoryBlocklistStore store = new InMemoryBlocklistStore(Clock.fixed(START, ZoneOffset.UTC));

        store.replaceRules(List.of(new BlocklistRule("1", BlocklistMatchType.IP, "10.0.0.1",
            null, true, "test")));

        assertThat(store.rules()).hasSize(1);
        store.replaceRules(List.of());
        assertThat(store.rules()).isEmpty();
    }

    @Test
    @DisplayName("窗口内达到阈值触发自动封禁，查询到期后自动解除")
    void autoBanAfterThreshold() {
        MutableClock clock = new MutableClock(START);
        InMemoryBlocklistStore store = new InMemoryBlocklistStore(clock);

        assertThat(store.recordViolation("1.2.3.4", Duration.ofSeconds(60), 3,
            Duration.ofSeconds(300), "brute force")).isEqualTo(1);
        assertThat(store.isBanned("1.2.3.4")).isFalse();
        store.recordViolation("1.2.3.4", Duration.ofSeconds(60), 3, Duration.ofSeconds(300), "brute force");
        store.recordViolation("1.2.3.4", Duration.ofSeconds(60), 3, Duration.ofSeconds(300), "brute force");

        assertThat(store.isBanned("1.2.3.4")).isTrue();
        assertThat(store.autoBans()).extracting(AutoBanEntry::clientIp).containsExactly("1.2.3.4");

        clock.advance(Duration.ofSeconds(301));
        assertThat(store.isBanned("1.2.3.4")).isFalse();
        assertThat(store.autoBans()).isEmpty();
    }

    @Test
    @DisplayName("窗口过期后计数从头开始")
    void windowResets() {
        MutableClock clock = new MutableClock(START);
        InMemoryBlocklistStore store = new InMemoryBlocklistStore(clock);

        store.recordViolation("1.2.3.4", Duration.ofSeconds(60), 2, Duration.ofSeconds(300), "x");
        clock.advance(Duration.ofSeconds(61));

        assertThat(store.recordViolation("1.2.3.4", Duration.ofSeconds(60), 2,
            Duration.ofSeconds(300), "x")).isEqualTo(1);
        assertThat(store.isBanned("1.2.3.4")).isFalse();
    }

    @Test
    @DisplayName("违规计数有容量上限，过期项会先被清理")
    void violationEntriesAreBounded() {
        MutableClock clock = new MutableClock(START);
        InMemoryBlocklistStore store = new InMemoryBlocklistStore(clock, 2);

        store.recordViolation("1.1.1.1", Duration.ofSeconds(60), 10, Duration.ofSeconds(60), "x");
        store.recordViolation("2.2.2.2", Duration.ofSeconds(60), 10, Duration.ofSeconds(60), "x");
        assertThat(store.recordViolation("3.3.3.3", Duration.ofSeconds(60), 10,
            Duration.ofSeconds(60), "x")).isZero();

        clock.advance(Duration.ofSeconds(61));
        assertThat(store.recordViolation("3.3.3.3", Duration.ofSeconds(60), 10,
            Duration.ofSeconds(60), "x")).isEqualTo(1);
    }

    @Test
    @DisplayName("手动封禁与解封")
    void manualBanAndUnban() {
        InMemoryBlocklistStore store = new InMemoryBlocklistStore(Clock.fixed(START, ZoneOffset.UTC));

        store.ban("1.2.3.4", Duration.ofMinutes(10), "manual");
        assertThat(store.isBanned("1.2.3.4")).isTrue();
        store.unban("1.2.3.4");
        assertThat(store.isBanned("1.2.3.4")).isFalse();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}

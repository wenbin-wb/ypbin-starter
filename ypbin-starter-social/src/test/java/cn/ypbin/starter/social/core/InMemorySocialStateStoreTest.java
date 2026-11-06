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
package cn.ypbin.starter.social.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link InMemorySocialStateStore} 用例。
 *
 * @author wenbin
 * @since 2026-11-06
 */
class InMemorySocialStateStoreTest {

    private static final Instant START = Instant.parse("2026-11-06T00:00:00Z");

    private static final class MutableClock extends Clock {

        private Instant instant = START;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }

    @Test
    @DisplayName("登记后可消费一次，重放失败")
    void consumeOnce() {
        InMemorySocialStateStore store = new InMemorySocialStateStore(
            Duration.ofMinutes(5), 10, Clock.fixed(START, ZoneOffset.UTC));
        store.save("state-1");

        assertThat(store.consume("state-1")).isTrue();
        assertThat(store.consume("state-1")).isFalse();
    }

    @Test
    @DisplayName("过期 state 视为无效")
    void expiredStateIsInvalid() {
        MutableClock clock = new MutableClock();
        InMemorySocialStateStore store = new InMemorySocialStateStore(Duration.ofMinutes(5), 10, clock);
        store.save("state-1");

        clock.advance(Duration.ofMinutes(5));

        assertThat(store.consume("state-1")).isFalse();
    }

    @Test
    @DisplayName("未知或空 state 一律无效")
    void unknownStateIsInvalid() {
        InMemorySocialStateStore store = new InMemorySocialStateStore();

        assertThat(store.consume("not-exists")).isFalse();
        assertThat(store.consume(null)).isFalse();
        assertThat(store.consume(" ")).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> store.save(" "));
    }

    @Test
    @DisplayName("容量满时淘汰最早到期项，映射保持有界")
    void evictsEarliestWhenFull() {
        MutableClock clock = new MutableClock();
        InMemorySocialStateStore store = new InMemorySocialStateStore(Duration.ofMinutes(5), 2, clock);
        store.save("state-1");
        clock.advance(Duration.ofSeconds(1));
        store.save("state-2");
        clock.advance(Duration.ofSeconds(1));
        store.save("state-3");

        assertThat(store.consume("state-1")).isFalse();
        assertThat(store.consume("state-2")).isTrue();
        assertThat(store.consume("state-3")).isTrue();
    }
}

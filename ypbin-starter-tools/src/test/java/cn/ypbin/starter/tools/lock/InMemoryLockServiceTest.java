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
package cn.ypbin.starter.tools.lock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * {@link InMemoryLockService} 单元测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class InMemoryLockServiceTest {

    private final InMemoryLockService lock = new InMemoryLockService();

    @Test
    void tryLockShouldSucceedThenBlockOthers() {
        assertThat(lock.tryLock("k", "owner-a", Duration.ofSeconds(10))).isTrue();
        // 同一 key 被他人占用时应失败
        assertThat(lock.tryLock("k", "owner-b", Duration.ofSeconds(10))).isFalse();
    }

    @Test
    void unlockShouldReleaseForHolder() {
        lock.tryLock("k", "owner-a", Duration.ofSeconds(10));
        assertThat(lock.unlock("k", "owner-a")).isTrue();
        // 释放后可再次抢占
        assertThat(lock.tryLock("k", "owner-b", Duration.ofSeconds(10))).isTrue();
    }

    @Test
    void unlockShouldRejectNonHolder() {
        lock.tryLock("k", "owner-a", Duration.ofSeconds(10));
        assertThat(lock.unlock("k", "owner-b")).isFalse();
    }

    @Test
    void unlockAbsentKeyShouldReturnFalse() {
        // key 不存在时释放应返回 false（区分于"成功释放"）
        assertThat(lock.unlock("nope", "owner-a")).isFalse();
    }

    @Test
    void expiredLockShouldBeReacquirable() throws InterruptedException {
        lock.tryLock("k", "owner-a", Duration.ofMillis(50));
        Thread.sleep(80);
        // 过期后他人可重新抢占
        assertThat(lock.tryLock("k", "owner-b", Duration.ofSeconds(10))).isTrue();
    }

    @Test
    void unlockExpiredLockByOwnerShouldEvictEntry() throws InterruptedException {
        lock.tryLock("k", "owner-a", Duration.ofMillis(50));
        Thread.sleep(80);
        // 超时后持有者释放：返回 false（已过期），但仍应把条目移除，防止动态 key 内存泄漏
        assertThat(lock.unlock("k", "owner-a")).isFalse();
        assertThat(lock.mapSize()).isZero();
    }

    // ------------------------------------------------------------------ 续期（可注入时钟，不真等）

    @Test
    void renewShouldPushExpiryForward() {
        MutableClock clock = new MutableClock(1_000_000L);
        InMemoryLockService timed = new InMemoryLockService(clock);
        timed.tryLock("k", "owner-a", Duration.ofSeconds(10));
        assertThat(timed.expireAtMillis("k")).isEqualTo(1_010_000L);

        clock.advance(4_000L);
        assertThat(timed.renew("k", "owner-a", Duration.ofSeconds(10))).isTrue();
        assertThat(timed.expireAtMillis("k"))
            .as("续期后到期时刻 = 当前时刻 + ttl")
            .isEqualTo(1_014_000L);
    }

    @Test
    void renewShouldRejectNonHolder() {
        MutableClock clock = new MutableClock(1_000_000L);
        InMemoryLockService timed = new InMemoryLockService(clock);
        timed.tryLock("k", "owner-a", Duration.ofSeconds(10));

        assertThat(timed.renew("k", "owner-b", Duration.ofSeconds(10))).isFalse();
        assertThat(timed.expireAtMillis("k")).as("他人续期不得改动到期时刻").isEqualTo(1_010_000L);
    }

    @Test
    void renewShouldRejectAbsentKeyAndNeverRecreateIt() {
        MutableClock clock = new MutableClock(1_000_000L);
        InMemoryLockService timed = new InMemoryLockService(clock);

        assertThat(timed.renew("missing", "owner-a", Duration.ofSeconds(10))).isFalse();
        assertThat(timed.mapSize()).as("续期绝不把不存在的锁造出来").isZero();
    }

    @Test
    void renewExpiredLockShouldFailAndNotReviveIt() {
        MutableClock clock = new MutableClock(1_000_000L);
        InMemoryLockService timed = new InMemoryLockService(clock);
        timed.tryLock("k", "owner-a", Duration.ofSeconds(10));

        clock.advance(10_000L);
        assertThat(timed.renew("k", "owner-a", Duration.ofSeconds(10)))
            .as("已过期的锁在语义上已不属于任何人，续期等于复活他人可能已抢到的锁")
            .isFalse();
    }
}

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
}

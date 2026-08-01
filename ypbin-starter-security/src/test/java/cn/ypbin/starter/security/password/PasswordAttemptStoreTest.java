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
package cn.ypbin.starter.security.password;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.security.password.lock.InMemoryPasswordAttemptStore;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * {@link InMemoryPasswordAttemptStore} 锁定时长刷新测试。
 *
 * @author wenbin
 * @since 2026-08-01
 */
class PasswordAttemptStoreTest {

    @Test
    void reachingThresholdRefreshesTtlToLockDuration() {
        InMemoryPasswordAttemptStore store = new InMemoryPasswordAttemptStore();
        String key = "k";
        // 观察窗口很短（2 秒），锁定时长很长（1 小时），阈值 3
        Duration window = Duration.ofSeconds(2);
        Duration lock = Duration.ofHours(1);

        store.increment(key, window, 3, lock);
        // 未达阈值：TTL 走观察窗口，<= 2 秒
        assertThat(store.getTimeToLiveSeconds(key)).isLessThanOrEqualTo(2);

        store.increment(key, window, 3, lock);
        long third = store.increment(key, window, 3, lock);

        // 达阈值：TTL 被刷新为满额锁定时长（≈1 小时），而非残留的观察窗口
        assertThat(third).isEqualTo(3);
        assertThat(store.getTimeToLiveSeconds(key)).isGreaterThan(3000);
    }

    @Test
    void belowThresholdKeepsObservationWindow() {
        InMemoryPasswordAttemptStore store = new InMemoryPasswordAttemptStore();
        String key = "k";
        store.increment(key, Duration.ofMinutes(10), 5, Duration.ofMinutes(10));
        store.increment(key, Duration.ofMinutes(10), 5, Duration.ofMinutes(10));
        assertThat(store.get(key)).isEqualTo(2);
        assertThat(store.getTimeToLiveSeconds(key)).isGreaterThan(0);
    }
}

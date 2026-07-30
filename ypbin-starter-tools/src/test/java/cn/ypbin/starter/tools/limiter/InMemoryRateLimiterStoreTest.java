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
package cn.ypbin.starter.tools.limiter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * {@link InMemoryRateLimiterStore} 单元测试。
 *
 * @author wenbin
 * @since 2026-07-30
 */
class InMemoryRateLimiterStoreTest {

    private final InMemoryRateLimiterStore store = new InMemoryRateLimiterStore();

    @Test
    void incrementAndGet_countsWithinWindow() {
        String key = "k1";
        Duration window = Duration.ofSeconds(10);
        assertThat(store.incrementAndGet(key, window)).isEqualTo(1L);
        assertThat(store.incrementAndGet(key, window)).isEqualTo(2L);
        assertThat(store.incrementAndGet(key, window)).isEqualTo(3L);
    }

    @Test
    void differentKeys_countIndependently() {
        Duration window = Duration.ofSeconds(10);
        assertThat(store.incrementAndGet("a", window)).isEqualTo(1L);
        assertThat(store.incrementAndGet("b", window)).isEqualTo(1L);
    }

    @Test
    void windowExpiry_resetsCount() throws InterruptedException {
        String key = "k2";
        Duration window = Duration.ofMillis(100);
        assertThat(store.incrementAndGet(key, window)).isEqualTo(1L);
        Thread.sleep(150);
        // 窗口过期后重新计数
        assertThat(store.incrementAndGet(key, window)).isEqualTo(1L);
    }

    @Test
    void concurrentIncrement_isThreadSafe() throws Exception {
        String key = "concurrent";
        Duration window = Duration.ofSeconds(30);
        int threads = 16;
        int perThread = 1000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicLong max = new AtomicLong();
        Future<?>[] futures = new Future<?>[threads];
        for (int t = 0; t < threads; t++) {
            futures[t] = pool.submit(() -> {
                for (int i = 0; i < perThread; i++) {
                    long v = store.incrementAndGet(key, window);
                    max.accumulateAndGet(v, Math::max);
                }
            });
        }
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
        // 无丢失、无重复：最终计数应恰好等于总次数
        assertThat(store.incrementAndGet(key, window)).isEqualTo((long) threads * perThread + 1);
    }
}

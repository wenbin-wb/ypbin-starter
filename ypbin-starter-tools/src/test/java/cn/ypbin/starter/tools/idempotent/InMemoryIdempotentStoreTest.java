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
package cn.ypbin.starter.tools.idempotent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * {@link InMemoryIdempotentStore} 单元测试。
 *
 * @author wenbin
 * @since 2026-07-30
 */
class InMemoryIdempotentStoreTest {

    private final InMemoryIdempotentStore store = new InMemoryIdempotentStore();

    @Test
    void firstAcquire_succeeds_repeatRejected() {
        String key = "order:1";
        Duration ttl = Duration.ofSeconds(10);
        assertThat(store.tryAcquire(key, ttl)).isTrue();
        assertThat(store.tryAcquire(key, ttl)).isFalse();
        assertThat(store.tryAcquire(key, ttl)).isFalse();
    }

    @Test
    void afterExpiry_canAcquireAgain() throws InterruptedException {
        String key = "order:2";
        assertThat(store.tryAcquire(key, Duration.ofMillis(100))).isTrue();
        Thread.sleep(150);
        assertThat(store.tryAcquire(key, Duration.ofMillis(100))).isTrue();
    }

    @Test
    void concurrentAcquire_onlyOneSucceeds() throws Exception {
        String key = "order:concurrent";
        Duration ttl = Duration.ofSeconds(30);
        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> store.tryAcquire(key, ttl));
        }
        List<Future<Boolean>> results = pool.invokeAll(tasks);
        pool.shutdown();

        AtomicInteger successCount = new AtomicInteger();
        for (Future<Boolean> f : results) {
            if (f.get()) {
                successCount.incrementAndGet();
            }
        }
        // 并发下有且只有一个线程占位成功
        assertThat(successCount.get()).isEqualTo(1);
    }
}

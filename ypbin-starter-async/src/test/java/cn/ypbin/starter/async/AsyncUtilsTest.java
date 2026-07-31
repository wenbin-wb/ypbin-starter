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
package cn.ypbin.starter.async;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.async.util.AsyncHolder;
import cn.ypbin.starter.async.util.AsyncUtils;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * {@link AsyncUtils} 单元测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class AsyncUtilsTest {

    private static ThreadPoolTaskScheduler scheduler;

    @BeforeAll
    static void bind() {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.initialize();
        // 直接用同步执行器，测试聚焦 AsyncUtils 编排逻辑
        AsyncHolder.bind(Runnable::run, scheduler);
    }

    @AfterAll
    static void cleanup() {
        scheduler.shutdown();
    }

    @Test
    void supplyShouldReturnResult() {
        CompletableFuture<Integer> future = AsyncUtils.supply(() -> 6 * 7);
        assertThat(future.join()).isEqualTo(42);
    }

    @Test
    void runShouldExecuteTask() {
        AtomicInteger counter = new AtomicInteger();
        AsyncUtils.run(counter::incrementAndGet).join();
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void thenShouldChain() {
        Integer result = AsyncUtils.then(AsyncUtils.supply(() -> 10), v -> v + 5).join();
        assertThat(result).isEqualTo(15);
    }

    @Test
    void combineShouldMerge() {
        Integer result = AsyncUtils.combine(AsyncUtils.supply(() -> 3), AsyncUtils.supply(() -> 4), Integer::sum).join();
        assertThat(result).isEqualTo(7);
    }

    @Test
    void withFallbackShouldRecover() {
        CompletableFuture<Integer> failed = AsyncUtils.supply(() -> {
            throw new IllegalStateException("boom");
        });
        Integer result = AsyncUtils.withFallback(failed, ex -> -1).join();
        assertThat(result).isEqualTo(-1);
    }

    @Test
    void supplyAllShouldKeepOrder() {
        List<Integer> results = AsyncUtils.supplyAll(List.of(() -> 1, () -> 2, () -> 3));
        assertThat(results).containsExactly(1, 2, 3);
    }

    @Test
    void mapAllShouldMapEachItem() {
        List<Integer> results = AsyncUtils.mapAll(List.of(1, 2, 3), v -> v * 10);
        assertThat(results).containsExactly(10, 20, 30);
    }

    @Test
    void runAllShouldRunEveryTask() {
        AtomicInteger counter = new AtomicInteger();
        AsyncUtils.runAll(List.of(counter::incrementAndGet, counter::incrementAndGet));
        assertThat(counter.get()).isEqualTo(2);
    }

    @Test
    void joinWithTimeoutShouldReturn() {
        Integer result = AsyncUtils.join(AsyncUtils.supply(() -> 99), Duration.ofSeconds(1));
        assertThat(result).isEqualTo(99);
    }

    @Test
    void scheduleShouldRunOnce() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger();
        AsyncUtils.schedule(counter::incrementAndGet, Duration.ofMillis(50));
        Thread.sleep(300);
        assertThat(counter.get()).isEqualTo(1);
    }
}

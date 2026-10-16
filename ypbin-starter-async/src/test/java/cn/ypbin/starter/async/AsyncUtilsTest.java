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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.async.util.AsyncHolder;
import cn.ypbin.starter.async.util.AsyncUtils;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
    void joinShouldUnwrapBusinessException() {
        CompletableFuture<Integer> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalArgumentException("bad arg"));

        // 业务异常应剥离为 CompletionException(真实异常)，而非嵌套 ExecutionException
        assertThatThrownBy(() -> AsyncUtils.join(failed, Duration.ofSeconds(1)))
            .isInstanceOf(CompletionException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class)
            .cause().hasMessage("bad arg");
    }

    @Test
    void joinShouldThrowOnTimeout() {
        CompletableFuture<Integer> never = new CompletableFuture<>();

        assertThatThrownBy(() -> AsyncUtils.join(never, Duration.ofMillis(100)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("超时");
    }

    // ------------------------------------------------------------------ joinAll 契约

    /**
     * 固定 {@code allOf} 的失败语义：必须等全部 future 结束后才抛出，抛出不提前于其余在途 future，
     * 抛出时集合内所有 future 均已结束（故不存在"级联取消"的对象）。
     *
     * <p>该用例是「joinAll 孤儿任务」判断的回归证据：若后续有人按错误前提改造 joinAll 为 fail-fast
     * 或加入空操作的 cancel 逻辑，此用例会失败并强制重新评估。
     */
    @Test
    void joinAllShouldWaitForAllFuturesBeforeThrowing() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            CompletableFuture<String> failing = CompletableFuture.supplyAsync(() -> {
                throw new IllegalStateException("boom");
            }, pool);
            CompletableFuture<String> slow = CompletableFuture.supplyAsync(() -> {
                slowStarted.countDown();
                awaitQuietly(release);
                return "late";
            }, pool);
            assertThat(slowStarted.await(5, TimeUnit.SECONDS)).isTrue();

            AtomicReference<Throwable> thrown = new AtomicReference<>();
            Thread joiner = new Thread(() -> {
                try {
                    AsyncUtils.joinAll(List.of(failing, slow));
                } catch (Throwable t) {
                    thrown.set(t);
                }
            });
            joiner.start();
            // failing 已失败，但 slow 未完成：joinAll 不应提前返回/抛出
            Thread.sleep(200);
            assertThat(thrown.get()).isNull();

            release.countDown();
            joiner.join(TimeUnit.SECONDS.toMillis(5));

            assertThat(joiner.isAlive()).isFalse();
            assertThat(thrown.get())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .cause().hasMessage("boom");
            // 抛出时 slow 已完成且未被取消 —— 集合内没有需要被级联取消的在途 future
            assertThat(slow).isCompletedWithValue("late");
            assertThat(slow).isNotCancelled();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    /** 失败语义保持不变：仍抛 CompletionException，cause 为真实业务异常。 */
    @Test
    void joinAllShouldPropagateFailureAsCompletionException() {
        CompletableFuture<Integer> failed = CompletableFuture.failedFuture(new IllegalArgumentException("bad arg"));

        assertThatThrownBy(() -> AsyncUtils.joinAll(List.of(failed)))
            .isInstanceOf(CompletionException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class)
            .cause().hasMessage("bad arg");
    }

    /** 其它批量入口（经 joinAll）异常语义一致。 */
    @Test
    void supplyAllShouldPropagateFailureAsCompletionException() {
        assertThatThrownBy(() -> AsyncUtils.supplyAll(List.of(() -> 1, () -> {
            throw new IllegalArgumentException("bad supplier");
        })))
            .isInstanceOf(CompletionException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scheduleShouldRunOnce() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger();
        AsyncUtils.schedule(counter::incrementAndGet, Duration.ofMillis(50));
        Thread.sleep(300);
        assertThat(counter.get()).isEqualTo(1);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

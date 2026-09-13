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
package cn.ypbin.starter.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link PersistCoordinator} 落盘合并与原子替换测试。
 *
 * <p>重点覆盖三件事：<b>不丢最终状态</b>（并发/防抖下最后一次变更必须落盘）、
 * <b>合并有效</b>（突发写入的落盘次数远小于变更次数）、<b>原子替换</b>（不残留临时文件）。</p>
 *
 * @author wenbin
 * @since 2026-09-13
 */
class PersistCoordinatorTest {

    /** 覆盖 10 轮，降低并发时序偶然性；每轮写入内容为当轮序号 */
    private static final int CONCURRENT_THREADS = 16;

    @Test
    @DisplayName("写透模式：每次变更即落盘")
    void writeThroughShouldPersistEveryChange(@TempDir Path dir) {
        Path target = dir.resolve("store.json");
        AtomicInteger writes = new AtomicInteger();
        PersistCoordinator coordinator = new PersistCoordinator(target.toString(), 0,
            file -> writeText(file, "v" + writes.incrementAndGet()));

        coordinator.markDirty();
        coordinator.markDirty();
        coordinator.markDirty();

        assertThat(writes.get()).isEqualTo(3);
        assertThat(readText(target)).isEqualTo("v3");
    }

    @Test
    @DisplayName("并发变更不丢最终状态，且落盘次数远小于变更次数")
    void concurrentChangesShouldNotLoseFinalState(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("store.json");
        AtomicInteger writes = new AtomicInteger();
        AtomicReference<String> latest = new AtomicReference<>("init");
        PersistCoordinator coordinator = new PersistCoordinator(target.toString(), 0, file -> {
            writes.incrementAndGet();
            writeText(file, latest.get());
        });

        try (ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_THREADS)) {
            CountDownLatch start = new CountDownLatch(1);
            for (int i = 0; i < CONCURRENT_THREADS; i++) {
                int index = i;
                pool.submit(() -> {
                    await(start);
                    latest.set("v" + index);
                    coordinator.markDirty();
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
        // 最后一个变更之后必须有一次落盘（可能由其他线程的收尾轮完成）
        coordinator.flush();

        assertThat(readText(target)).isEqualTo(latest.get());
        // 无论并发多少，单飞 + 合并都不应退化为「每个线程各写一次」
        assertThat(writes.get()).isLessThanOrEqualTo(CONCURRENT_THREADS);
    }

    @Test
    @DisplayName("防抖模式：窗口内多次变更只落盘一次，flush 补写剩余变更")
    void debounceShouldCoalesceSequentialChanges(@TempDir Path dir) throws Exception {
        Path target = dir.resolve("store.json");
        AtomicInteger writes = new AtomicInteger();
        PersistCoordinator coordinator = new PersistCoordinator(target.toString(), 200,
            file -> writeText(file, "v" + writes.incrementAndGet()));

        for (int i = 0; i < 20; i++) {
            coordinator.markDirty();
        }
        // 静默期内不应已经写出
        assertThat(writes.get()).isZero();

        coordinator.flush();
        assertThat(writes.get()).isEqualTo(1);

        // 再变更一次并等待静默期：调度器自动合并落盘
        coordinator.markDirty();
        long deadline = System.currentTimeMillis() + 5000;
        while (writes.get() < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertThat(writes.get()).isEqualTo(2);
        coordinator.close();
    }

    @Test
    @DisplayName("原子替换：目标文件为最新内容且不残留临时文件")
    void shouldReplaceAtomicallyWithoutTempLeftover(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("store.json");
        PersistCoordinator coordinator = new PersistCoordinator(target.toString(), 0,
            file -> writeText(file, "payload"));

        coordinator.markDirty();

        assertThat(readText(target)).isEqualTo("payload");
        try (var entries = Files.list(dir)) {
            assertThat(entries.map(path -> path.getFileName().toString()).toList())
                .containsExactly("store.json");
        }
    }

    @Test
    @DisplayName("路径为空时不落盘（未配置持久化）")
    void shouldNoopWhenStorePathBlank(@TempDir Path dir) {
        AtomicInteger writes = new AtomicInteger();
        PersistCoordinator coordinator = new PersistCoordinator("  ", 0,
            file -> writes.incrementAndGet());

        coordinator.markDirty();
        coordinator.flush();
        coordinator.close();

        assertThat(writes.get()).isZero();
    }

    @Test
    @DisplayName("写入失败不抛出（持久化尽力而为），且不残留临时文件")
    void writeFailureShouldBeSwallowedAndCleanTemp(@TempDir Path dir) throws IOException {
        Path target = dir.resolve("store.json");
        PersistCoordinator coordinator = new PersistCoordinator(target.toString(), 0, file -> {
            writeText(file, "partial");
            throw new IllegalStateException("序列化失败");
        });

        coordinator.markDirty();

        assertThat(Files.exists(target)).isFalse();
        try (var entries = Files.list(dir)) {
            assertThat(entries.toList()).as("失败后不应残留临时文件").isEmpty();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void writeText(File file, String text) {
        try {
            Files.writeString(file.toPath(), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("测试写入失败", e);
        }
    }

    private static String readText(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("测试读取失败", e);
        }
    }
}

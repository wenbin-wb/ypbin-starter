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

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 向量库落盘协调器：合并突发写入 + 原子替换文件。
 *
 * <p><strong>要解决的问题</strong>：向量库持久化是「整库序列化」。每次 {@code add()} 都同步全量落盘时，
 * 批量入库 N 批就是 O(N²) 的序列化与磁盘写（第 k 批要重写前 k 批的全部数据）。</p>
 *
 * <p><strong>两种合并（都不丢数据）</strong>：</p>
 * <ol>
 *   <li><b>并发合并（恒生效）</b>：同一时刻只有单飞线程真正写文件，写入期间到达的变更只额外触发
 *       一轮落盘，且循环复查脏标记——并发突发 N 次写入通常只落盘 1~2 次，且最后一次变更必然落盘。</li>
 *   <li><b>防抖合并（可选，{@code debounceMillis > 0} 时启用）</b>：把「每次变更立即写」改为
 *       「静默期后合并写一次」，从而把顺序 N 次变更的写出次数从 N 降到约 1，消除 O(N²)。
 *       代价是硬崩溃（SIGKILL/断电）时可能丢失最近一个防抖窗口内的变更，因此默认关闭
 *       （{@code debounceMillis = 0} 即写透）；正常关闭由 {@link #flush()} 保证落盘。</li>
 * </ol>
 *
 * <p><strong>原子替换</strong>：先写同目录临时文件，再以 {@code ATOMIC_MOVE} 覆盖目标文件，
 * 避免进程在写文件中途退出留下半个 JSON（下次启动 {@code load()} 直接失败、向量数据不可用）。</p>
 *
 * @author wenbin
 * @since 2026-09-13
 */
final class PersistCoordinator {

    private static final Logger log = LoggerFactory.getLogger(PersistCoordinator.class);

    /** 临时文件后缀：与目标同目录，保证 ATOMIC_MOVE 在同一文件系统内生效 */
    private static final String TEMP_SUFFIX = ".tmp";

    private final String storePath;

    /** 实际写入逻辑（由调用方提供，如 {@code delegate.save(file)}） */
    private final Consumer<File> writer;

    /** 防抖间隔（毫秒）；0 表示写透（每次变更立即写） */
    private final long debounceMillis;

    /** 是否有待落盘的变更 */
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    /** 是否已有线程在落盘（单飞标志） */
    private final AtomicBoolean persisting = new AtomicBoolean(false);

    /** 防抖调度器（懒创建，daemon 线程不阻塞 JVM 退出） */
    private volatile ScheduledExecutorService scheduler;

    /** 待执行的防抖落盘任务 */
    private volatile ScheduledFuture<?> pendingFlush;

    /**
     * 构建协调器。
     *
     * @param storePath     目标文件路径；为空表示不持久化（所有操作空操作）
     * @param debounceMillis 防抖间隔（毫秒），0 表示写透
     * @param writer        实际写入逻辑
     */
    PersistCoordinator(String storePath, long debounceMillis, Consumer<File> writer) {
        this.storePath = storePath;
        this.debounceMillis = Math.max(0, debounceMillis);
        this.writer = writer;
    }

    /**
     * 标记有待落盘变更。
     *
     * <p>写透模式下：返回时本次变更已落盘（或被另一线程合并处理）。
     * 防抖模式下：返回时已安排静默期后合并落盘，可用 {@link #flush()} 强制立即落盘。</p>
     */
    void markDirty() {
        if (!enabled()) {
            return;
        }
        dirty.set(true);
        if (debounceMillis > 0) {
            scheduleFlush();
            return;
        }
        drain();
    }

    /**
     * 强制把当前脏数据落盘并等待完成（用于正常关闭、或调用方需要明确持久化点时）。
     */
    void flush() {
        if (!enabled()) {
            return;
        }
        ScheduledFuture<?> pending = pendingFlush;
        if (pending != null) {
            pending.cancel(false);
            pendingFlush = null;
        }
        drain();
    }

    /**
     * 释放调度器（幂等）。
     */
    void close() {
        ScheduledExecutorService current = scheduler;
        if (current != null) {
            current.shutdownNow();
            scheduler = null;
        }
    }

    private boolean enabled() {
        return storePath != null && !storePath.isBlank();
    }

    /**
     * 立即合并落盘：单飞 + 循环复查脏标记 + 释放标志后复查，保证不丢最终状态。
     */
    private void drain() {
        if (!persisting.compareAndSet(false, true)) {
            // 已有线程在落盘：它会观察到本次脏标记并处理，直接返回
            return;
        }
        try {
            do {
                // 先清标记再写入：写入期间的变更会把标记重新置位，从而在下一轮被处理
                dirty.set(false);
                write();
            } while (dirty.get());
        } finally {
            persisting.set(false);
        }
        // 关闭竞态窗口：检查脏标记为 false 之后、释放单飞标志之前若有新变更，这里复查重试，
        // 保证「最后一次变更一定落盘」——这是「合并」与「丢数据」的分界线
        if (dirty.get()) {
            drain();
        }
    }

    /** 安排静默期后合并落盘；窗口内重复调用只保留一个任务 */
    private void scheduleFlush() {
        if (pendingFlush != null && !pendingFlush.isDone()) {
            return;
        }
        synchronized (this) {
            if (pendingFlush != null && !pendingFlush.isDone()) {
                return;
            }
            pendingFlush = scheduler().schedule(() -> {
                pendingFlush = null;
                drain();
            }, debounceMillis, TimeUnit.MILLISECONDS);
        }
    }

    private ScheduledExecutorService scheduler() {
        ScheduledExecutorService current = scheduler;
        if (current == null) {
            synchronized (this) {
                if (scheduler == null) {
                    scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                        Thread thread = new Thread(runnable, "ypbin-vector-persist");
                        thread.setDaemon(true);
                        return thread;
                    });
                }
                current = scheduler;
            }
        }
        return current;
    }

    /** 写入一次：临时文件 + 原子替换 */
    private void write() {
        File target = new File(storePath);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            log.warn("[ypbin-ai] 无法创建向量持久化目录: {}", parent);
            return;
        }
        File temp = new File(parent, target.getName() + TEMP_SUFFIX);
        try {
            writer.accept(temp);
            moveAtomically(temp.toPath(), target.toPath());
            log.debug("[ypbin-ai] SimpleVectorStore saved to {}", storePath);
        } catch (RuntimeException e) {
            // 序列化失败不阻断本次会话的向量检索，仅记录日志（持久化是尽力而为）
            log.warn("[ypbin-ai] 向量持久化失败（不影响本次会话检索）: {}", e.getMessage());
            deleteQuietly(temp);
        }
    }

    /**
     * 原子替换目标文件；文件系统不支持原子移动时退化为普通替换。
     *
     * @param source 临时文件
     * @param target 目标文件
     */
    private static void moveAtomically(Path source, Path target) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException fallback) {
                throw new IllegalStateException("向量持久化文件替换失败：" + target, fallback);
            }
        } catch (IOException e) {
            throw new IllegalStateException("向量持久化文件替换失败：" + target, e);
        }
    }

    private static void deleteQuietly(File file) {
        if (file.exists() && !file.delete()) {
            log.debug("[ypbin-ai] 临时文件清理失败（可忽略）: {}", file);
        }
    }
}

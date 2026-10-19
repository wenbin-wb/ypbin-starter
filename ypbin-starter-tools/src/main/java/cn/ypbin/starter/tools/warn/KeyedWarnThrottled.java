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
package cn.ypbin.starter.tools.warn;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.slf4j.Logger;

/**
 * <b>按键分桶</b>的 WARN 节流器：每个 key 独立计时、独立计数。
 *
 * <p><b>与 {@link WarnThrottled} 的区别</b>：{@code WarnThrottled} 是「整条告警一个窗口」，
 * 适合「全局只有一个同类告警源」；而本类按 key 分桶，适合「<b>每个点位/设备/租户各有一个窗口</b>」
 * 的场景——例如「某设备的某个点位取值异常」告警：若共用一个窗口，一台设备刷屏会把其它设备的
 * 告警全部抑制掉，那等于<b>静默丢失</b>其余故障。</p>
 *
 * <p><b>为什么需要它</b>（2026-10-19 反哺自 iot）：{@code ypbin-iot} 的
 * {@code AvailabilityServiceImpl.SeriesValueWarnThrottle} 因缺少本能力而自建了一份
 * 「按点位键分桶 + 可注入时钟」的节流器（约 30 行）；这是典型的「通用能力焊在业务仓」。
 * 上收到本类后宿主只需一行构造 + 调用。</p>
 *
 * <p><b>为什么用纳秒时钟</b>：分桶场景的告警间隔常为分钟级，但调用频率可能是每秒级；
 * 纳秒时钟（{@link System#nanoTime()}）不受系统时间回拨影响，比 {@link Clock#millis()}
 * 更适合纯间隔判定。若需要「跨重启保持」或「人可读时间」语义，应改用 {@link WarnThrottled}。</p>
 *
 * <p><b>内存增长</b>：窗口 Map 按 key 增长且<b>不做淘汰</b>——key 的基数必须是有界的
 * （设备数、点位数这类稳定集合）。<b>不要</b>用请求 ID / 用户输入这类无界值做 key，
 * 否则会持续占用内存。key 无界时应由调用方自行限流或改用固定分片。</p>
 *
 * @author wenbin
 * @since 2026-10-19
 */
public final class KeyedWarnThrottled {

    private final long minIntervalNanos;

    private final Logger log;

    private final LongSupplier nanoClock;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * 构造按键分桶的节流器（生产用：纳秒时钟取 {@link System#nanoTime()}）。
     *
     * @param minIntervalMillis 同一 key 的最小输出间隔（毫秒），不得为负；0 表示不节流
     * @param log               实际输出 WARN 的日志器
     */
    public KeyedWarnThrottled(long minIntervalMillis, Logger log) {
        this(minIntervalMillis, log, System::nanoTime);
    }

    /**
     * 构造按键分桶的节流器并注入纳秒时钟（用于确定性测试）。
     *
     * @param minIntervalMillis 同一 key 的最小输出间隔（毫秒），不得为负；0 表示不节流
     * @param log               日志器
     * @param nanoClock         纳秒时钟（如 {@link System#nanoTime()}）
     */
    public KeyedWarnThrottled(long minIntervalMillis, Logger log, LongSupplier nanoClock) {
        if (minIntervalMillis < 0) {
            // 负数间隔等价于「永远不节流」是配置错误，直接拒绝而不是静默放行（禁静默降级）
            throw new IllegalArgumentException("minIntervalMillis 不能为负数: " + minIntervalMillis);
        }
        this.minIntervalNanos = minIntervalMillis * 1_000_000L;
        this.log = Objects.requireNonNull(log, "log");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
    }

    /**
     * 按 key 节流地输出一条 WARN。
     *
     * @param key    分桶键（同一 key 共享一个窗口），不可为 {@code null}
     * @param format SLF4J 格式串（占位符 {@code {}}），不可为 {@code null}
     * @param args   格式参数；与 SLF4J 一致，末位为 {@link Throwable} 时按异常输出
     * @return 本次是否真的输出了日志（{@code false} = 被节流抑制）
     */
    public boolean tryWarn(String key, String format, Object... args) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(format, "format");
        Window window = windows.computeIfAbsent(key, ignored -> new Window());
        if (!window.tryAcquire(nanoClock.getAsLong(), minIntervalNanos)) {
            return false;
        }
        log.warn(format, args);
        return true;
    }

    /**
     * 该 key 上「本次放行前被抑制的条数」，<b>读出即清零</b>。
     *
     * <p>语义与 iot 的既有实现一致：被抑制的调用返回 {@code -1}，放行时返回此前累计的抑制数
     * （便于在 WARN 里带「此前同类被抑制 N 条」）。未出现过的 key 返回 0。</p>
     *
     * @param key 分桶键
     * @return 本次放行前被抑制的条数；本次被抑制返回 {@code -1}
     */
    public long acquire(String key) {
        Objects.requireNonNull(key, "key");
        Window window = windows.computeIfAbsent(key, ignored -> new Window());
        return window.tryAcquireWithSuppressed(nanoClock.getAsLong(), minIntervalNanos);
    }

    /**
     * 该 key 的累计抑制条数（不清零，供指标上报）。
     *
     * @param key 分桶键
     * @return 累计抑制条数；未出现过的 key 返回 0
     */
    public long suppressedCount(String key) {
        Window window = windows.get(key);
        return window == null ? 0L : window.suppressedTotal.get();
    }

    /**
     * 当前已分配的窗口数量（供内存/基数监控：无界的 key 会让它持续增长）。
     *
     * @return 窗口数
     */
    public int windowCount() {
        return windows.size();
    }

    /** 单个 key 的窗口状态。 */
    private static final class Window {

        /** 下次允许输出的纳秒时间点（0 表示从未输出过，首次必放行）。 */
        private final AtomicLong nextAllowedNanos = new AtomicLong();

        /** 自上次放行以来被抑制的条数（放行时清零，用于「此前被抑制 N 条」）。 */
        private final AtomicLong suppressedSinceLast = new AtomicLong();

        /** 累计抑制条数（单调递增，供指标）。 */
        private final AtomicLong suppressedTotal = new AtomicLong();

        boolean tryAcquire(long nowNanos, long minIntervalNanos) {
            return tryAcquireWithSuppressed(nowNanos, minIntervalNanos) >= 0;
        }

        long tryAcquireWithSuppressed(long nowNanos, long minIntervalNanos) {
            while (true) {
                long next = nextAllowedNanos.get();
                // 未输出过（next=0）或已到期：放行，并把窗口推进到 now+interval
                if (next == 0L || nowNanos >= next) {
                    if (nextAllowedNanos.compareAndSet(next, nowNanos + minIntervalNanos)) {
                        return suppressedSinceLast.getAndSet(0L);
                    }
                    continue;
                }
                // 窗口内：抑制并计数
                suppressedSinceLast.incrementAndGet();
                suppressedTotal.incrementAndGet();
                return -1L;
            }
        }
    }
}

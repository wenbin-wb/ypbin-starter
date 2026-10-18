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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 按最小间隔节流的 WARN 输出器（线程安全、无 Spring 依赖）。
 *
 * <p>用途是「同一个 key 的告警按最小间隔打，避免刷爆日志」这一在宿主里被反复手写的模式：
 * 「队列满、租约抖动、对账不一致」这类问题一旦发生往往是持续的，每来一条就 WARN 会把日志写满，
 * 反而淹没真正的线索。把节流逻辑收敛到本类，宿主只需持有实例并在异常路径调用
 * {@link #tryWarn(String, Object...)}。</p>
 *
 * <p><b>语义</b>：距上次真正输出不足 {@code minIntervalMillis} 的调用被抑制（计入
 * {@link #suppressedCount()} 累计值）并返回 {@code false}；到达间隔的调用真的输出并返回 {@code true}。
 * 返回值的用途是让调用方/测试能断言「这次到底打了没有」，不必去解析日志。</p>
 *
 * <p><b>为什么用 CAS 而不是 synchronized</b>：热路径（每次丢一条读数、每次对账失败）不该抢锁；
 * 只有「时间戳推进」这一小步需要原子性，用 {@link AtomicLong#compareAndSet} 完成即可，冲突时重试一次
 * 是廉价的。</p>
 *
 * <p><b>时钟回拨</b>：若系统时间被回拨到「上次输出之前」，按「已到期」处理并立即输出——
 * 否则要等到时钟追平才恢复告警，等于故障期间静默。宁可多打一条也不静默（禁静默降级）。</p>
 *
 * <p><b>指标</b>：本类不依赖 micrometer；需要把抑制条数上报时，调用方读
 * {@link #suppressedCount()} 自行注册 Gauge 即可（避免为此把可观测性依赖引进工具类）。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
public final class WarnThrottled {

    /** 从未输出过的哨兵值：不能与「上次输出毫秒数」混淆，故取 {@link Long#MIN_VALUE}。 */
    private static final long NEVER_WARNED = Long.MIN_VALUE;

    private static final Logger DEFAULT_LOGGER = LoggerFactory.getLogger(WarnThrottled.class);

    /** 最小输出间隔（毫秒）；0 表示不节流。 */
    private final long minIntervalMillis;

    private final Logger log;

    private final Clock clock;

    /** 上次真正输出的时间戳（毫秒），{@link #NEVER_WARNED} 表示尚未输出。 */
    private final AtomicLong lastWarnAtMillis = new AtomicLong(NEVER_WARNED);

    /** 被抑制的累计条数（自构造起单调递增）。 */
    private final AtomicLong suppressed = new AtomicLong();

    /**
     * 构造节流器（日志器名取本类，适合「框架自身告警」）。
     *
     * @param minIntervalMillis 最小输出间隔（毫秒），不得为负；0 表示不节流
     */
    public WarnThrottled(long minIntervalMillis) {
        this(minIntervalMillis, DEFAULT_LOGGER, Clock.systemUTC());
    }

    /**
     * 构造节流器（把日志分类到调用方指定的日志器，便于按业务组件过滤）。
     *
     * @param minIntervalMillis 最小输出间隔（毫秒），不得为负；0 表示不节流
     * @param log               实际输出 WARN 的日志器
     */
    public WarnThrottled(long minIntervalMillis, Logger log) {
        this(minIntervalMillis, log, Clock.systemUTC());
    }

    /**
     * 测试用构造器：注入时钟，使「跨间隔」这类时序语义可确定性验证。
     *
     * @param minIntervalMillis 最小输出间隔（毫秒）
     * @param log               日志器
     * @param clock             时钟
     */
    WarnThrottled(long minIntervalMillis, Logger log, Clock clock) {
        if (minIntervalMillis < 0) {
            // 负数间隔等价于「永远不节流」是配置错误，直接拒绝而不是静默放行（禁静默降级）
            throw new IllegalArgumentException("minIntervalMillis 不能为负数: " + minIntervalMillis);
        }
        this.minIntervalMillis = minIntervalMillis;
        this.log = Objects.requireNonNull(log, "log");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 按间隔节流地输出一条 WARN。
     *
     * @param format SLF4J 格式串（占位符 {@code {}}），不可为 {@code null}
     * @param args   格式参数；与 SLF4J 一致，末位为 {@link Throwable} 时按异常输出
     * @return 本次是否真的输出了日志（{@code false} = 被节流抑制）
     */
    public boolean tryWarn(String format, Object... args) {
        Objects.requireNonNull(format, "format");
        long now = clock.millis();
        while (true) {
            long last = lastWarnAtMillis.get();
            if (last != NEVER_WARNED && now >= last && now - last < minIntervalMillis) {
                suppressed.incrementAndGet();
                return false;
            }
            if (lastWarnAtMillis.compareAndSet(last, now)) {
                log.warn(format, args);
                return true;
            }
            // 并发下被别的线程抢先推进了时间戳：重读后按新状态重新判定
        }
    }

    /**
     * 被抑制的累计条数（自构造起单调递增，用于在 WARN 里带「已抑制 N 条」或注册为 Gauge）。
     *
     * @return 累计抑制条数
     */
    public long suppressedCount() {
        return suppressed.get();
    }

    /**
     * 最小输出间隔（毫秒）。
     *
     * @return 间隔；0 表示不节流
     */
    public long minIntervalMillis() {
        return minIntervalMillis;
    }
}

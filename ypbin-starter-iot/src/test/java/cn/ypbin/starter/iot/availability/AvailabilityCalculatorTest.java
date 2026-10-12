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
package cn.ypbin.starter.iot.availability;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.iot.availability.AvailabilityCalculator.Summary;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 可用率计算纯逻辑单测（M-2 口径）。下沉自 ypbin-iot，行为与原实现一致。
 *
 * <p>自 A11 起，断档秒数由 SQL 精确聚合给出（原业务仓 {@code OutageEventMapper.summarizeInWindow}
 * 负责按窗口裁剪），因此这里测的是「拿到精确输入后」的三件防御：窗口为 0 不误判、逐项下限 0
 * （脏数据不得抬高可用率）、合计封顶到窗口（可用率不得为负），以及双条件达标。</p>
 *
 * @author wenbin
 * @since 3.8.1
 */
class AvailabilityCalculatorTest {

    private static final long WINDOW = 10 * 3600L;
    private static final long INTERVAL_MS = 5_000L;

    @Test
    @DisplayName("无断档：可用率 100% 且达标")
    void noOutageMeansFullAvailability() {
        Summary summary = AvailabilityCalculator.summarize(WINDOW, 0L, 0L, 0L, 0L, 0, INTERVAL_MS, false);

        assertThat(summary.windowSeconds()).isEqualTo(WINDOW);
        assertThat(summary.outageSeconds()).isZero();
        assertThat(summary.longestOutageSeconds()).isZero();
        assertThat(summary.availability()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(summary.meetsTarget()).isTrue();
        assertThat(summary.truncated()).isFalse();
    }

    @Test
    @DisplayName("窗口内 1 小时断档：可用率 = 1 - 3600/36000 = 0.9，不达标")
    void outageMustReduceAvailability() {
        Summary summary = AvailabilityCalculator.summarize(WINDOW, 0L, 3_600L, 3_600L, 0L, 1, INTERVAL_MS, false);

        assertThat(summary.outageSeconds()).isEqualTo(3_600L);
        assertThat(summary.longestOutageSeconds()).isEqualTo(3_600L);
        assertThat(summary.outageCount()).isEqualTo(1);
        assertThat(summary.availability()).isEqualByComparingTo(new BigDecimal("0.900000"));
        assertThat(summary.meetsTarget()).isFalse();
    }

    @Test
    @DisplayName("★ 整段停机：断档合计 = 统计总时长 ⇒ 可用率 0、不达标，且不得为负")
    void wholeWindowOutageMeansZeroAvailability() {
        Summary summary = AvailabilityCalculator.summarize(WINDOW, 0L, WINDOW, WINDOW, 0L, 1, INTERVAL_MS, false);

        assertThat(summary.effectiveWindowSeconds()).isEqualTo(WINDOW);
        assertThat(summary.outageSeconds()).isEqualTo(WINDOW);
        assertThat(summary.longestOutageSeconds()).isEqualTo(WINDOW);
        assertThat(summary.availability()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.availability().signum()).isNotNegative();
        assertThat(summary.meetsTarget()).isFalse();
    }

    @Test
    @DisplayName("★ 断档与窗口部分重叠：调用方已按窗口裁剪，只计窗口内那段秒数")
    void partiallyOverlappingOutageCountsOnlyIntersection() {
        // 一圈更长的断档被 SQL 裁剪后只留下 30 分钟落在本窗口内 ⇒ 可用率 = 1 - 1800/36000 = 0.95
        Summary summary = AvailabilityCalculator.summarize(WINDOW, 0L, 1_800L, 44_000L, 0L, 1, INTERVAL_MS,
            false);

        assertThat(summary.outageSeconds()).as("窗口内计入的断档段").isEqualTo(1_800L);
        assertThat(summary.longestOutageSeconds()).as("最长单次也被合计封顶").isEqualTo(1_800L);
        assertThat(summary.availability()).isEqualByComparingTo(new BigDecimal("0.950000"));
    }

    @Test
    @DisplayName("★ 跨窗口多次停机：按合计计入，次数是精确值，最长单次独立保留")
    void multipleOutagesAcrossWindowAreAggregated() {
        // 72h 窗口、3 次停机合计 600s（最长 300s）⇒ 可用率 ≈ 99.7685%，且最长 300s ≤ 600s ⇒ 达标
        long window = 72 * 3600L;
        Summary summary = AvailabilityCalculator.summarize(window, 0L, 600L, 300L, 0L, 3, INTERVAL_MS, false);

        assertThat(summary.outageCount()).isEqualTo(3);
        assertThat(summary.outageSeconds()).isEqualTo(600L);
        assertThat(summary.longestOutageSeconds()).isEqualTo(300L);
        assertThat(summary.availability()).isEqualByComparingTo(new BigDecimal("0.997685"));
        assertThat(summary.meetsTarget()).isTrue();
    }

    @Test
    @DisplayName("★ 脏数据：断档合计超过窗口时封顶到窗口（可用率不得为负）")
    void outageBeyondWindowMustBeClamped() {
        Summary summary = AvailabilityCalculator.summarize(WINDOW, 0L, WINDOW * 3, WINDOW * 2, 0L, 2, INTERVAL_MS,
            false);

        assertThat(summary.outageSeconds()).as("合计封顶").isEqualTo(WINDOW);
        assertThat(summary.longestOutageSeconds()).as("最长也封顶到合计").isEqualTo(WINDOW);
        assertThat(summary.availability()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.availability().signum()).isNotNegative();
    }

    @Test
    @DisplayName("★ 脏数据：负数断档不得把可用率抬高（逐项下限 0）")
    void negativeOutageMustNotInflateAvailability() {
        Summary summary = AvailabilityCalculator.summarize(WINDOW, 0L, -5_000L, -5_000L, 0L, 3, INTERVAL_MS, false);

        assertThat(summary.outageSeconds()).isZero();
        assertThat(summary.longestOutageSeconds()).isZero();
        assertThat(summary.availability()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("★ 达标是双条件：可用率够高但单次断档超上限 ⇒ 不达标")
    void meetsTargetMustRequireBothConditions() {
        // 窗口 72h + 断档 15 分钟 ⇒ 可用率 99.65%（≥99.5%），但最长断档 900s > 600s（10×5s=50s ⇒ 取下限）
        long window = 72 * 3600L;
        Summary summary = AvailabilityCalculator.summarize(window, 0L, 900L, 900L, 0L, 1, INTERVAL_MS, false);

        assertThat(summary.availability()).isEqualByComparingTo(new BigDecimal("0.996528"));
        assertThat(summary.availability()).isGreaterThanOrEqualTo(AvailabilityRules.TARGET_AVAILABILITY);
        assertThat(summary.longestOutageSeconds()).isEqualTo(900L);
        assertThat(summary.maxAllowedOutageSeconds()).isEqualTo(600L);
        assertThat(summary.meetsTarget()).as("可用率达标但最长断档 900s > 600s ⇒ 不达标").isFalse();
    }

    @Test
    @DisplayName("窗口时长为 0（区间给反/相等）：不判不达标、可用率记 100%，不把参数错误装成重大断档")
    void emptyWindowMustNotBeReportedAsOutage() {
        Summary summary = AvailabilityCalculator.summarize(0L, 0L, 600L, 600L, 0L, 1, INTERVAL_MS, false);

        assertThat(summary.windowSeconds()).isZero();
        assertThat(summary.effectiveWindowSeconds()).isZero();
        assertThat(summary.outageSeconds()).isZero();
        assertThat(summary.availability()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(summary.meetsTarget()).isTrue();
    }

    @Test
    @DisplayName("★ 退化输入：负窗口（区间给反）同样不误判为全窗口断档，可用率记 100%")
    void negativeWindowMustNotBeReportedAsOutage() {
        Summary summary = AvailabilityCalculator.summarize(-100L, 0L, 600L, 600L, 0L, 1, INTERVAL_MS, false);

        assertThat(summary.windowSeconds()).isEqualTo(-100L);
        assertThat(summary.effectiveWindowSeconds()).isZero();
        assertThat(summary.outageSeconds()).isZero();
        assertThat(summary.availability()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(summary.meetsTarget()).isTrue();
    }

    @Test
    @DisplayName("★ 退化输入：采集周期为 0 时最长断档上限退回 10 分钟下限，不因此把一个正常窗口判为不达标")
    void zeroIntervalMustFallBackToFloor() {
        Summary summary = AvailabilityCalculator.summarize(WINDOW, 0L, 0L, 0L, 0L, 0, 0L, false);

        assertThat(summary.maxAllowedOutageSeconds()).isEqualTo(AvailabilityRules.MAX_OUTAGE_FLOOR_SECONDS);
        assertThat(summary.availability()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(summary.meetsTarget()).isTrue();
    }

    @Test
    @DisplayName("★ 维护窗口：计划停机从**分母**里排除（分母变小、可用率不被计划停机拉低）")
    void maintenanceMustShrinkStatisticsWindow() {
        // 10h 窗口里 2h 计划维护、0 断档 ⇒ 统计总时长 8h、可用率 100%
        Summary summary = AvailabilityCalculator.summarize(WINDOW, 7_200L, 0L, 0L, 0L, 0, INTERVAL_MS, false);

        assertThat(summary.maintenanceSeconds()).isEqualTo(7_200L);
        assertThat(summary.effectiveWindowSeconds()).as("分母 = 窗口 − 维护").isEqualTo(WINDOW - 7_200L);
        assertThat(summary.availability()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(summary.meetsTarget()).isTrue();
    }

    @Test
    @DisplayName("★ 维护窗口：断档落在维护内的部分**也从分子里剔除**（否则计划停机仍拉低可用率）")
    void outageInsideMaintenanceMustBeExcludedFromNumerator() {
        // 10h 窗口：2h 维护；计入断档 0s（另 3_600s 断档落在维护内被剔除）
        Summary summary = AvailabilityCalculator.summarize(WINDOW, 7_200L, 0L, 0L, 3_600L, 1, INTERVAL_MS,
            false);

        assertThat(summary.outageSeconds()).as("计入断档（已排除维护内部分）").isZero();
        assertThat(summary.outageInMaintenanceSeconds()).as("被剔除的部分要能解释").isEqualTo(3_600L);
        assertThat(summary.availability()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("★ 维护窗口完全覆盖：整段都在维护里 ⇒ 无统计时长，不判不达标（不得伪装成全窗口断档）")
    void fullyMaintainedWindowMustNotBeReportedAsOutage() {
        Summary summary = AvailabilityCalculator.summarize(WINDOW, WINDOW, WINDOW, WINDOW, WINDOW, 1,
            INTERVAL_MS, false);

        assertThat(summary.effectiveWindowSeconds()).isZero();
        assertThat(summary.outageSeconds()).isZero();
        assertThat(summary.availability()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(summary.meetsTarget()).isTrue();
    }

    @Test
    @DisplayName("★ 维护窗口：维护时长超过窗口时封顶到窗口（统计总时长不得为负）")
    void maintenanceBeyondWindowMustBeClamped() {
        Summary summary = AvailabilityCalculator.summarize(WINDOW, WINDOW * 2, 100L, 100L, 0L, 1,
            INTERVAL_MS, false);

        assertThat(summary.maintenanceSeconds()).isEqualTo(WINDOW);
        assertThat(summary.effectiveWindowSeconds()).isZero();
        assertThat(summary.availability()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("★ 明细截断不影响汇总：truncated 只透传，断档秒数/次数照旧（这是 A11 的核心）")
    void truncatedDetailsMustNotChangeSummary() {
        Summary summary = AvailabilityCalculator.summarize(WINDOW, 0L, 6_030L, 30L, 0L, 201, INTERVAL_MS, true);

        assertThat(summary.truncated()).isTrue();
        assertThat(summary.outageCount()).as("次数是精确值，不因为明细只返回 200 条而变成 200")
            .isEqualTo(201);
        assertThat(summary.outageSeconds()).as("汇总来自精确聚合").isEqualTo(6_030L);
        assertThat(summary.availability()).isEqualByComparingTo(new BigDecimal("0.832500"));
    }
}

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

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 可用率计算（纯逻辑，无 IO）：把断档窗口按统计窗口裁剪后聚合。
 * 下沉自 ypbin-iot（原实现与 {@link AvailabilityRules} 同址、口径同源）。
 *
 * <p>口径见 {@link AvailabilityRules}。三处刻意写死的边界：</p>
 * <ol>
 *   <li><b>窗口时长为 0</b>（{@code from >= to}，通常是把区间给反了）：不判不达标、可用率记 100%——
 *       否则「参数写反」会伪装成「设备全窗口断档」；</li>
 *   <li><b>断档与窗口求交</b>：只统计落在窗口内的那一段，跨窗口的长断档不会被整段计入；</li>
 *   <li><b>防御性封顶</b>：明细重叠或脏数据导致断档合计超过窗口时，按窗口上限截断，保证可用率 ≥ 0
 *       （宁可高估可用率也不给出负数这种无意义的值，且该情形由调用方记录告警）。</li>
 * </ol>
 *
 * @author wenbin
 * @since 3.8.1
 */
public final class AvailabilityCalculator {

    private AvailabilityCalculator() {
    }

    /**
     * 可用率汇总。
     *
     * @param windowSeconds            统计窗口（墙钟，秒）
     * @param effectiveWindowSeconds   统计**总时长**（= 窗口 − 维护窗口，秒）——可用率的分母
     * @param maintenanceSeconds       窗口内的维护时长（秒，分母被排除的部分）
     * @param outageSeconds            计入的断档合计（秒；**已排除**落在维护窗口内的部分）
     * @param outageInMaintenanceSeconds 被排除的断档（秒；落在维护窗口内，仅用于解释）
     * @param longestOutageSeconds     计入的最长单次断档（秒；同样已排除维护内的部分）
     * @param outageCount              窗口内断档次数（精确值，与明细条数无关）
     * @param availability             可用率 = 1 − 计入断档 / 统计总时长
     * @param meetsTarget              是否达标（双条件）
     * @param maxAllowedOutageSeconds  最长单次断档上限（秒）
     * @param truncated                断档**明细**是否被截断（不影响本汇总的精确性）
     */
    public record Summary(long windowSeconds, long effectiveWindowSeconds, long maintenanceSeconds,
                          long outageSeconds, long outageInMaintenanceSeconds, long longestOutageSeconds,
                          int outageCount, BigDecimal availability, boolean meetsTarget,
                          long maxAllowedOutageSeconds, boolean truncated) {
    }

    /**
     * 由**精确的**窗口、维护窗口与断档秒数算可用率。
     *
     * <p>断档秒数由调用方按窗口裁剪、并**剔除落在维护窗口内的部分**后给出
     * （原业务仓由 {@code OutageEventMapper.summarizeInWindow} 的 SQL 聚合负责）——
     * 不再从「最多 N 条」的明细里求和：明细一截断，求和就会低估断档 ⇒ 可用率偏高。这里只做四件防御：</p>
     * <ol>
     *   <li><b>统计总时长为 0</b>（窗口全被维护覆盖，或 {@code from >= to}）：不判不达标、可用率记 100%
     *       ——否则「整段是计划停机」会伪装成「设备全窗口断档」；</li>
     *   <li><b>逐项下限 0</b>：脏数据（{@code end < start}）不得让断档为负、进而把可用率抬高；</li>
     *   <li><b>封顶到统计总时长</b>：明细重叠等脏数据导致断档合计超过统计总时长时按它截断，保证可用率 ≥ 0；</li>
     *   <li><b>被排除的维护断档也取下限</b>：{@code outageInMaintenance} 只用于解释，不得为负。</li>
     * </ol>
     *
     * @param windowSeconds              统计窗口（墙钟，秒）
     * @param maintenanceSeconds         窗口内维护时长（秒）
     * @param outageSeconds              计入的断档合计（秒，已排除维护内的部分；可为负=脏数据）
     * @param longestOutageSeconds       计入的最长单次断档（秒）
     * @param outageInMaintenanceSeconds 被排除的断档（秒；落在维护窗口内）
     * @param outageCount                断档次数
     * @param intervalMs                 生效采集周期（毫秒）
     * @param truncated                  明细是否被截断
     * @return 汇总
     */
    public static Summary summarize(long windowSeconds, long maintenanceSeconds, long outageSeconds,
                                    long longestOutageSeconds, long outageInMaintenanceSeconds,
                                    int outageCount, long intervalMs, boolean truncated) {
        long maxAllowed = AvailabilityRules.maxAllowedOutageSeconds(intervalMs);
        long maintenance = Math.max(0L, Math.min(maintenanceSeconds, windowSeconds));
        long effectiveWindow = windowSeconds - maintenance;
        long excluded = Math.max(0L, outageInMaintenanceSeconds);
        if (effectiveWindow <= 0L) {
            // 整段都是计划维护（或窗口本来就为空）：没有可统计的时长 ⇒ 不判不达标
            return new Summary(windowSeconds, 0L, maintenance, 0L, excluded, 0L, Math.max(0, outageCount),
                BigDecimal.ONE.setScale(AvailabilityRules.AVAILABILITY_SCALE), true, maxAllowed, truncated);
        }
        long total = Math.max(0L, Math.min(outageSeconds, effectiveWindow));
        long longest = Math.max(0L, Math.min(longestOutageSeconds, total));
        BigDecimal availability = BigDecimal.ONE.subtract(BigDecimal.valueOf(total)
            .divide(BigDecimal.valueOf(effectiveWindow), AvailabilityRules.AVAILABILITY_SCALE,
                RoundingMode.HALF_UP));
        if (availability.signum() < 0) {
            availability = BigDecimal.ZERO.setScale(AvailabilityRules.AVAILABILITY_SCALE);
        }
        boolean meets = availability.compareTo(AvailabilityRules.TARGET_AVAILABILITY) >= 0
            && longest <= maxAllowed;
        return new Summary(windowSeconds, effectiveWindow, maintenance, total, excluded, longest,
            Math.max(0, outageCount), availability, meets, maxAllowed, truncated);
    }
}

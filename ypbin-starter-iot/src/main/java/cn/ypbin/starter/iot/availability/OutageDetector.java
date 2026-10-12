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

import java.time.Duration;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * 断档判定（纯逻辑，无 IO）：给「最近有效数据时刻/首次观测时刻 + 采集周期 + K + 当前时刻」出结论。
 * 下沉自 ypbin-iot（原实现与 {@link AvailabilityRules} 同址、口径同源）。
 *
 * <p>抽成纯函数是为了能用可推进的时间直接测边界（刚好等于阈值不算断档、超 1 秒就算），
 * 而不是靠 sleep 或真实时钟。</p>
 *
 * @author wenbin
 * @since 3.8.1
 */
public final class OutageDetector {

    private OutageDetector() {
    }

    /**
     * 生效采集周期：未提供或非正时退回兜底周期。
     *
     * @param pollIntervalMs     上报的采集周期（可空）
     * @param fallbackIntervalMs 兜底周期（必须为正，由启动自检保证）
     * @return 生效周期（毫秒）
     */
    public static long effectiveIntervalMs(@Nullable Integer pollIntervalMs, long fallbackIntervalMs) {
        return pollIntervalMs == null || pollIntervalMs <= 0 ? fallbackIntervalMs : pollIntervalMs;
    }

    /**
     * 断档起点：最后一次有效数据；从未有过有效数据时退化为「首次观测」。
     *
     * <p>为什么不退化为「窗口起点」：设备刚接入时不该按「从盘古开天辟地起就没数据」算断档；
     * 首次观测之前平台根本不知道它的存在。</p>
     *
     * @param lastGoodAt      最近有效数据时刻（可空）
     * @param firstObservedAt 首次观测时刻（可空）
     * @return 断档起点；两者都空时返回 {@code null}（无法判定）
     */
    @Nullable
    public static LocalDateTime outageStart(@Nullable LocalDateTime lastGoodAt,
                                            @Nullable LocalDateTime firstObservedAt) {
        return lastGoodAt != null ? lastGoodAt : firstObservedAt;
    }

    /**
     * 是否已构成断档：{@code now - start > K × 周期}（**严格大于**，刚好等于阈值不算）。
     *
     * @param start      断档起点（可空）
     * @param now        当前时刻（通常取数据库时钟；可空）
     * @param intervalMs 生效采集周期（毫秒）
     * @param kFactor    倍数 K
     * @return 构成断档返回 {@code true}
     */
    public static boolean isOutage(@Nullable LocalDateTime start, @Nullable LocalDateTime now,
                                   long intervalMs, int kFactor) {
        if (start == null || now == null || intervalMs <= 0 || kFactor <= 0) {
            return false;
        }
        return Duration.between(start, now).toMillis() > kFactor * intervalMs;
    }
}

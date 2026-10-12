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

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 断档判定纯逻辑单测（M-2）：边界必须能用可推进的时间精确断言。
 * 下沉自 ypbin-iot，行为与原实现一致。
 *
 * @author wenbin
 * @since 3.8.1
 */
class OutageDetectorTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 9, 22, 10, 0, 0);

    @Test
    @DisplayName("★ 阈值边界：now - start 恰好等于 K×周期**不算**断档，多 1 毫秒才算")
    void thresholdMustBeStrictlyGreater() {
        assertThat(OutageDetector.isOutage(T0, T0.plusSeconds(10), 5_000L, 2))
            .as("恰好 2×5000ms=10s：不算断档（避免周期抖动即报断档）").isFalse();
        assertThat(OutageDetector.isOutage(T0, T0.plusSeconds(10).plusNanos(1_000_000L), 5_000L, 2))
            .as("多 1ms：算断档").isTrue();
    }

    @Test
    @DisplayName("起点缺失、现在缺失、周期非正、K 非正：一律不判断档（不猜）")
    void malformedInputsMustNotClaimOutage() {
        assertThat(OutageDetector.isOutage(null, T0, 5_000L, 2)).isFalse();
        assertThat(OutageDetector.isOutage(T0, null, 5_000L, 2)).isFalse();
        assertThat(OutageDetector.isOutage(T0, T0.plusDays(1), 0L, 2)).isFalse();
        assertThat(OutageDetector.isOutage(T0, T0.plusDays(1), -1L, 2)).isFalse();
        assertThat(OutageDetector.isOutage(T0, T0.plusDays(1), 5_000L, 0)).isFalse();
    }

    @Test
    @DisplayName("退化输入：start/now 同一时刻（差 0）不算断档；周期为 0 时任何正差也不当成断档")
    void zeroLengthAndZeroPeriodMustNotClaimOutage() {
        assertThat(OutageDetector.isOutage(T0, T0, 5_000L, 2)).isFalse();
        assertThat(OutageDetector.isOutage(T0, T0.plusDays(365), 0L, 1)).isFalse();
    }

    @Test
    @DisplayName("生效周期：未上报/0/负数退回兜底；正数原样使用")
    void effectiveIntervalMustFallBackWhenMissing() {
        assertThat(OutageDetector.effectiveIntervalMs(null, 5_000L)).isEqualTo(5_000L);
        assertThat(OutageDetector.effectiveIntervalMs(0, 5_000L)).isEqualTo(5_000L);
        assertThat(OutageDetector.effectiveIntervalMs(-3, 5_000L)).isEqualTo(5_000L);
        assertThat(OutageDetector.effectiveIntervalMs(1_000, 5_000L)).isEqualTo(1_000L);
    }

    @Test
    @DisplayName("断档起点：优先最后一次有效数据；从未有过有效数据才退化为首次观测")
    void outageStartMustPreferLastGood() {
        assertThat(OutageDetector.outageStart(T0, T0.minusHours(1))).isEqualTo(T0);
        assertThat(OutageDetector.outageStart(null, T0.minusHours(1))).isEqualTo(T0.minusHours(1));
        assertThat(OutageDetector.outageStart(null, null)).isNull();
    }

    @Test
    @DisplayName("最长断档上限：max(10 分钟, 10 × 采集周期)——小周期取下限、大周期取倍数")
    void maxAllowedOutageMustFollowBothBranches() {
        assertThat(AvailabilityRules.maxAllowedOutageSeconds(5_000L)).as("10×5s=50s < 600s ⇒ 取下限")
            .isEqualTo(600L);
        assertThat(AvailabilityRules.maxAllowedOutageSeconds(120_000L)).as("10×120s=1200s > 600s ⇒ 取倍数")
            .isEqualTo(1_200L);
    }
}

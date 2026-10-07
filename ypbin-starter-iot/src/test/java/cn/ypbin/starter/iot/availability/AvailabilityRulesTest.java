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
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 可用率口径用例（下沉口径，行为与 ypbin-iot 原实现一致）。
 *
 * @author wenbin
 * @since 3.8.0
 */
class AvailabilityRulesTest {

    @Test
    @DisplayName("口径常量：目标 0.995 / 6 位小数 / 平台东八区")
    void constantsMustMatchSpec() {
        assertThat(AvailabilityRules.TARGET_AVAILABILITY.toPlainString()).isEqualTo("0.995");
        assertThat(AvailabilityRules.AVAILABILITY_SCALE).isEqualTo(6);
        assertThat(AvailabilityRules.PLATFORM_ZONE).isEqualTo(ZoneId.of("GMT+8"));
        assertThat(AvailabilityRules.QUALITY_GOOD).isEqualTo("GOOD");
    }

    @Test
    @DisplayName("epoch 毫秒转平台墙上时间；null 回 null")
    void toLocalDateTimeMustUsePlatformZone() {
        assertThat(AvailabilityRules.toLocalDateTime(null)).isNull();
        LocalDateTime actual = AvailabilityRules.toLocalDateTime(0L);
        assertThat(actual).isEqualTo(LocalDateTime.of(1970, 1, 1, 8, 0, 0));
    }

    @Test
    @DisplayName("断档上限 = max(600s, 10×周期秒)，周期归一至少 1 秒")
    void maxAllowedOutageMustTakeFloorAndMultiple() {
        assertThat(AvailabilityRules.maxAllowedOutageSeconds(0L)).isEqualTo(600L);
        assertThat(AvailabilityRules.maxAllowedOutageSeconds(15_000L)).isEqualTo(600L);
        assertThat(AvailabilityRules.maxAllowedOutageSeconds(60_000L)).isEqualTo(600L);
        assertThat(AvailabilityRules.maxAllowedOutageSeconds(120_000L)).isEqualTo(1200L);
    }
}

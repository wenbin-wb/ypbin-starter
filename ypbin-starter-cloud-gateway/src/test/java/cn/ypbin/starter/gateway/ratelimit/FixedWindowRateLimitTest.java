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
package cn.ypbin.starter.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 固定窗口判定用例。
 *
 * @author wenbin
 * @since 2026-10-02
 */
class FixedWindowRateLimitTest {

    @Test
    @DisplayName("计数在配额内放行；超限拒绝；0/负配额 = 不限")
    void decisionBounds() {
        assertThat(FixedWindowRateLimit.forCount(5, 10).allowed()).isTrue();
        assertThat(FixedWindowRateLimit.forCount(10, 10).allowed()).isTrue();
        assertThat(FixedWindowRateLimit.forCount(11, 10).allowed()).isFalse();
        assertThat(FixedWindowRateLimit.forCount(999, 0).allowed()).isTrue();
        assertThat(FixedWindowRateLimit.forCount(9999, -1).allowed()).isTrue();
    }

    @Test
    @DisplayName("判定结果携带计数与配额（供日志/审计）")
    void decisionCarriesCounts() {
        FixedWindowRateLimit decision = FixedWindowRateLimit.forCount(3, 5);
        assertThat(decision.count()).isEqualTo(3L);
        assertThat(decision.limit()).isEqualTo(5L);
    }
}

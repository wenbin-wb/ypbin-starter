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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.jspecify.annotations.Nullable;

/**
 * 可用率口径的<b>固定部分</b>（下沉自 ypbin-iot）。
 *
 * <ul>
 *   <li>有效数据 = {@code quality=GOOD}；</li>
 *   <li>断档 = 连续 &gt; K × 采集周期 无有效数据（K 可配，由调用方供给）；</li>
 *   <li>可用率（逐台）= 1 - Σ(断档时长 ∩ 统计窗口) / 窗口时长（进行中的断档按查询时刻结算）；</li>
 *   <li>达标是<b>双条件</b>：可用率 ≥ {@link #TARGET_AVAILABILITY} 且最长单次断档 ≤
 *       {@code max(10 分钟, 10 × 采集周期)}。</li>
 * </ul>
 *
 * <p>这些是<b>口径</b>而非部署参数，故写死为常量并随响应返回（客户端不必重复实现一遍口径，
 * 避免两处口径漂移）。</p>
 *
 * @author wenbin
 * @since 3.8.0
 */
public final class AvailabilityRules {

    /** 有效数据的质量码（与协议栈 {@code Quality.name()} 对齐；IoT 服务不依赖协议栈，故用常量）。 */
    public static final String QUALITY_GOOD = "GOOD";

    /** 目标可用率。 */
    public static final BigDecimal TARGET_AVAILABILITY = new BigDecimal("0.995");

    /** 最长单次断档的下限（秒）：max(10 分钟, 10 × 采集周期)。 */
    public static final long MAX_OUTAGE_FLOOR_SECONDS = 600L;

    /** 最长单次断档的采集周期倍数。 */
    public static final long MAX_OUTAGE_INTERVAL_MULTIPLIER = 10L;

    /** 可用率小数位。 */
    public static final int AVAILABILITY_SCALE = 6;

    /**
     * 平台时区（与 starter 的全局时间序列化一致）。
     *
     * <p>显式写死而不用 {@code systemDefault()}：上报来的是 epoch 毫秒，落库要变成 {@code LocalDateTime}，
     * 若依赖 JVM 默认时区，同一份数据在不同时区的节点上会落成不同的「墙上时间」——这类差异在报表上
     * 很难定位。</p>
     */
    public static final ZoneId PLATFORM_ZONE = ZoneId.of("GMT+8");

    /** 维护窗口回显上限（响应里解释口径用；不影响统计精度）。 */
    public static final int MAX_MAINTENANCE_ROWS = 50;

    /** 断档明细一次返回的最大条数（超出则截断并置 {@code truncated}，避免一个长窗口把响应撑爆）。 */
    public static final int MAX_OUTAGE_ROWS = 200;

    private AvailabilityRules() {
    }

    /**
     * epoch 毫秒 → 平台墙上时间。
     *
     * @param epochMillis epoch 毫秒（可空）
     * @return 平台时区的 {@code LocalDateTime}；入参为空返回 {@code null}
     */
    @Nullable
    public static LocalDateTime toLocalDateTime(@Nullable Long epochMillis) {
        return epochMillis == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis),
            PLATFORM_ZONE);
    }

    /**
     * 最长单次断档上限（秒）：{@code max(10 分钟, 10 × 采集周期)}。
     *
     * @param intervalMs 生效采集周期（毫秒，必须为正）
     * @return 上限秒数
     */
    public static long maxAllowedOutageSeconds(long intervalMs) {
        long intervalSeconds = Math.max(1L, Math.round(intervalMs / 1000.0d));
        return Math.max(MAX_OUTAGE_FLOOR_SECONDS, MAX_OUTAGE_INTERVAL_MULTIPLIER * intervalSeconds);
    }
}

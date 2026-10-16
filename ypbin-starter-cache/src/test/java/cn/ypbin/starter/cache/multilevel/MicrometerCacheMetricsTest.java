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
package cn.ypbin.starter.cache.multilevel;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link MicrometerCacheMetrics} 测试：锁定指标名、标签维度与计数口径（接入方按这些名字做 Prometheus 告警，
 * 属对外契约）。
 *
 * @author wenbin
 * @since 2026-10-17
 */
class MicrometerCacheMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final MicrometerCacheMetrics metrics = MicrometerCacheMetrics.of(registry, "test-cache");

    @Test
    @DisplayName("读请求按 level+result 两个维度分别计数")
    void requestsAreCountedByLevelAndResult() {
        metrics.l1Hit();
        metrics.l1Hit();
        metrics.l1Miss();
        metrics.l2Hit();
        metrics.l2Miss();

        assertThat(count(MicrometerCacheMetrics.REQUESTS_METRIC, "l1", "hit")).isEqualTo(2.0);
        assertThat(count(MicrometerCacheMetrics.REQUESTS_METRIC, "l1", "miss")).isEqualTo(1.0);
        assertThat(count(MicrometerCacheMetrics.REQUESTS_METRIC, "l2", "hit")).isEqualTo(1.0);
        assertThat(count(MicrometerCacheMetrics.REQUESTS_METRIC, "l2", "miss")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("空值哨兵拦截与 L2 装载分别计数")
    void sentinelAndLoadAreCounted() {
        metrics.nullSentinelHit();
        metrics.load();
        metrics.load();

        Counter sentinel = Objects.requireNonNull(registry.find(MicrometerCacheMetrics.NULL_SENTINEL_METRIC)
            .tag("cache", "test-cache").counter());
        Counter loads = Objects.requireNonNull(registry.find(MicrometerCacheMetrics.LOADS_METRIC)
            .tag("cache", "test-cache").counter());

        assertThat(sentinel.count()).isEqualTo(1.0);
        assertThat(loads.count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("重复注册同一 cache 名不产生新序列（Micrometer 幂等），计数累加")
    void repeatedRegistrationIsIdempotent() {
        MicrometerCacheMetrics second = MicrometerCacheMetrics.of(registry, "test-cache");
        metrics.l1Hit();
        second.l1Hit();

        assertThat(count(MicrometerCacheMetrics.REQUESTS_METRIC, "l1", "hit")).isEqualTo(2.0);
        assertThat(registry.getMeters()).hasSize(6);
    }

    private double count(String name, String level, String result) {
        Counter counter = registry.find(name)
            .tag("cache", "test-cache")
            .tag("level", level)
            .tag("result", result)
            .counter();
        assertThat(counter).as("指标 %s(level=%s,result=%s) 应已注册", name, level, result).isNotNull();
        return Objects.requireNonNull(counter).count();
    }
}

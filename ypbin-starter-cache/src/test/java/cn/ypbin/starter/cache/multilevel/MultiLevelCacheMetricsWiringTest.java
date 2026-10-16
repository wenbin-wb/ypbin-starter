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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link MultiLevelCacheService} 的指标埋点测试：用记录型 {@link CacheMetricsRecorder} 断言读路径各分支
 * 真的产生了对应指标（防「埋点写了但分支没走到」的假接线）。
 *
 * @author wenbin
 * @since 2026-10-17
 */
class MultiLevelCacheMetricsWiringTest {

    private final RecordingRecorder recorder = new RecordingRecorder();

    private final MultiLevelCacheServiceTest.FakeL2 l2 = new MultiLevelCacheServiceTest.FakeL2();

    private final Cache<String, Object> l1 = Caffeine.newBuilder().build();

    private final MultiLevelCacheService service = new MultiLevelCacheService(l2, l1, null, recorder);

    @Test
    @DisplayName("L1 命中：只记 l1.hit，不碰 L2")
    void l1HitIsCounted() {
        l1.put("k", "v");

        assertThat(service.get("k", String.class)).isEqualTo("v");

        assertThat(recorder.l1Hits).isEqualTo(1);
        assertThat(recorder.l1Misses).isZero();
        assertThat(l2.getCount).hasValue(0);
    }

    @Test
    @DisplayName("★ L2 未命中后回填空值哨兵：第二次请求被哨兵拦截（防穿透阻断）且不再打到 L2")
    void nullSentinelBlocksSecondRequest() {
        assertThat(service.get("missing", String.class)).isNull();
        assertThat(service.get("missing", String.class)).isNull();

        assertThat(recorder.l1Misses).as("首次 L1 未命中").isEqualTo(1);
        assertThat(recorder.l2Misses).as("首次 L2 未命中").isEqualTo(1);
        assertThat(recorder.l1Hits).as("第二次命中哨兵").isEqualTo(1);
        assertThat(recorder.nullSentinelHits).as("第二次被防穿透阻断").isEqualTo(1);
        assertThat(l2.getCount).hasValue(1);
    }

    @Test
    @DisplayName("L2 命中：记 l1.miss + l2.hit")
    void l2HitIsCounted() {
        l2.store.put("k", "v");

        assertThat(service.get("k", String.class)).isEqualTo("v");

        assertThat(recorder.l1Misses).isEqualTo(1);
        assertThat(recorder.l2Hits).isEqualTo(1);
        assertThat(recorder.l2Misses).isZero();
    }

    @Test
    @DisplayName("getOrLoad：记 l1.miss + load，第二次走 L1 命中不再 load")
    void getOrLoadCountsLoadOnce() {
        assertThat(service.getOrLoad("k", String.class, () -> "loaded", Duration.ofSeconds(10)))
            .isEqualTo("loaded");
        assertThat(service.getOrLoad("k", String.class, () -> "other", Duration.ofSeconds(10)))
            .isEqualTo("loaded");

        assertThat(recorder.l1Misses).isEqualTo(1);
        assertThat(recorder.loads).isEqualTo(1);
        assertThat(recorder.l1Hits).isEqualTo(1);
    }

    /** 记录各事件调用次数的替身。 */
    static final class RecordingRecorder implements CacheMetricsRecorder {

        int l1Hits;

        int l1Misses;

        int l2Hits;

        int l2Misses;

        int nullSentinelHits;

        int loads;

        @Override
        public void l1Hit() {
            l1Hits++;
        }

        @Override
        public void l1Miss() {
            l1Misses++;
        }

        @Override
        public void l2Hit() {
            l2Hits++;
        }

        @Override
        public void l2Miss() {
            l2Misses++;
        }

        @Override
        public void nullSentinelHit() {
            nullSentinelHits++;
        }

        @Override
        public void load() {
            loads++;
        }
    }
}

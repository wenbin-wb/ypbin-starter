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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    @DisplayName("★ 宿主 recorder 抛 RuntimeException：缓存读/回源/写语义全部不变（埋点异常被隔离）")
    void throwingRecorderDoesNotChangeCacheSemantics() {
        ThrowingRecorder throwing = new ThrowingRecorder();
        MultiLevelCacheService guarded = new MultiLevelCacheService(l2, l1, null, throwing);

        // L1 命中（l1Hit / nullSentinelHit 埋点均抛）
        l1.put("hit", "v");
        assertThat(guarded.get("hit", String.class)).isEqualTo("v");
        // L1 未命中 + L2 命中（l1Miss / l2Hit 埋点均抛）
        l2.store.put("l2key", "l2value");
        assertThat(guarded.get("l2key", String.class)).isEqualTo("l2value");
        // L2 未命中后回填哨兵，第二次被哨兵拦截（l1Miss / l2Miss 埋点均抛）
        assertThat(guarded.get("missing", String.class)).isNull();
        assertThat(guarded.get("missing", String.class)).isNull();
        // 回源路径（load 埋点抛）
        assertThat(guarded.getOrLoad("load", String.class, () -> "loaded", Duration.ofSeconds(10)))
            .isEqualTo("loaded");
        // 写路径（setIfAbsent，埋点不参与但必须仍写入 L2）
        assertThat(guarded.setIfAbsent("w", "wv", Duration.ofSeconds(10))).isTrue();
        assertThat(l2.store).containsEntry("w", "wv");

        assertThat(throwing.attempts)
            .as("埋点必须真的被调用过（异常被隔离，而不是整段埋点被静默跳过）")
            .isPositive();
    }

    @Test
    @DisplayName("★ 埋点抛 Error：原样抛出（防御层只隔离 RuntimeException，不掩盖进程级故障）")
    void errorFromRecorderIsNotSwallowed() {
        MultiLevelCacheService guarded = new MultiLevelCacheService(l2, l1, null, new ErrorThrowingRecorder());
        l1.put("k", "v");

        assertThatThrownBy(() -> guarded.get("k", String.class))
            .isInstanceOf(AssertionError.class)
            .hasMessageContaining("进程级故障");
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

    /** 每个埋点方法都抛运行时异常的替身：模拟宿主自定义 recorder 违约（接口声明不得抛异常）。 */
    static final class ThrowingRecorder implements CacheMetricsRecorder {

        int attempts;

        @Override
        public void l1Hit() {
            throw boom();
        }

        @Override
        public void l1Miss() {
            throw boom();
        }

        @Override
        public void l2Hit() {
            throw boom();
        }

        @Override
        public void l2Miss() {
            throw boom();
        }

        @Override
        public void nullSentinelHit() {
            throw boom();
        }

        @Override
        public void load() {
            throw boom();
        }

        private IllegalStateException boom() {
            attempts++;
            return new IllegalStateException("埋点后端不可用");
        }
    }

    /** 埋点抛 {@link Error} 的替身：验证防御层不吞 JVM 级故障。 */
    static final class ErrorThrowingRecorder implements CacheMetricsRecorder {

        @Override
        public void l1Hit() {
            throw new AssertionError("进程级故障不得被指标防御层吞掉");
        }

        @Override
        public void l1Miss() {
        }

        @Override
        public void l2Hit() {
        }

        @Override
        public void l2Miss() {
        }

        @Override
        public void nullSentinelHit() {
        }

        @Override
        public void load() {
        }
    }
}

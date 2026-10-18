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
package cn.ypbin.starter.core.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link MetricNames} 与 {@link MetricsSupport} 单元测试。
 *
 * @author wenbin
 * @since 2026-10-18
 */
class MetricNamesTest {

    @Test
    void ofShouldPrefixWithYpbin() {
        assertThat(MetricNames.of("cache", "requests")).isEqualTo("ypbin.cache.requests");
        assertThat(MetricNames.of("lock", "skipped")).isEqualTo("ypbin.lock.skipped");
        assertThat(MetricNames.of("cache", "null.sentinel.hits")).isEqualTo("ypbin.cache.null.sentinel.hits");
        assertThat(MetricNames.PREFIX).isEqualTo("ypbin");
    }

    @Test
    void tagKeysShouldBeTheAgreedOnes() {
        assertThat(MetricNames.TAG_TYPE).isEqualTo("type");
        assertThat(MetricNames.TAG_RESULT).isEqualTo("result");
        assertThat(MetricNames.TAG_LEVEL).isEqualTo("level");
        assertThat(MetricNames.TAG_MODULE).isEqualTo("module");
    }

    @Test
    void ofShouldRejectInvalidSegments() {
        assertThatThrownBy(() -> MetricNames.of(null, "requests")).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("module");
        assertThatThrownBy(() -> MetricNames.of("  ", "requests")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MetricNames.of("cache", "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MetricNames.of("cache", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MetricNames.of("ca che", "requests")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MetricNames.of("cache", "requests(")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MetricNames.of(".cache", "requests")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MetricNames.of("cache", "requests.")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MetricNames.of("cache", "re..quests")).isInstanceOf(IllegalArgumentException.class);
        // 首尾空白属调用方拼错，静默 strip 会让埋点名与代码里看到的不一致
        assertThatThrownBy(() -> MetricNames.of(" cache", "requests")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void counterShouldUseConventionNameAndAutoModuleTag() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        Counter counter = MetricsSupport.counter(registry, "cache", "requests", "level", "l1", "result", "hit");
        counter.increment();

        assertThat(registry.get("ypbin.cache.requests").counter().count()).isEqualTo(1.0);
        Meter meter = registry.get("ypbin.cache.requests").meter();
        assertThat(meter.getId().getTag("module")).isEqualTo("cache");
        assertThat(meter.getId().getTag("level")).isEqualTo("l1");
        assertThat(meter.getId().getTag("result")).isEqualTo("hit");
    }

    @Test
    void incrementShouldAdvanceCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        MetricsSupport.increment(registry, "lock", "skipped", "task", "alert-evaluate");
        MetricsSupport.increment(registry, "lock", "skipped", "task", "alert-evaluate");

        assertThat(registry.get("ypbin.lock.skipped").counter().count()).isEqualTo(2.0);
    }

    @Test
    void tagOrderShouldBeStableRegardlessOfCallerArgumentOrder() {
        // 同一组标签、不同传参顺序必须落到同一个 Meter（标签按 key 排序）
        String[] first = {"type", "egress", "result", "dropped"};
        String[] second = {"result", "dropped", "type", "egress"};

        assertThat(MetricsSupport.tags("access", first))
            .isEqualTo(MetricsSupport.tags("access", second));

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MetricsSupport.counter(registry, "access", "errors", first);
        MetricsSupport.counter(registry, "access", "errors", second);
        assertThat(registry.getMeters()).hasSize(1);
    }

    @Test
    void tagsShouldRejectInvalidInput() {
        assertThatThrownBy(() -> MetricsSupport.tags("cache", "level"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("成对");
        assertThatThrownBy(() -> MetricsSupport.tags("cache", " ", "l1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("标签 key");
        assertThatThrownBy(() -> MetricsSupport.tags("cache", "level", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("标签 value");
        assertThatThrownBy(() -> MetricsSupport.tags("cache", "level", "l1", "level", "l2"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("重复");
        assertThatThrownBy(() -> MetricsSupport.tags("cache", MetricNames.TAG_MODULE, "other"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("自动补齐");
        assertThatThrownBy(() -> MetricsSupport.tags("ca che"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tagsShouldOnlyCarryModuleWhenNoExtraTags() {
        assertThat(MetricsSupport.tags("cache")).isEqualTo(Tags.of("module", "cache"));
        assertThat(MetricsSupport.tags("cache", (String[]) null)).isEqualTo(Tags.of("module", "cache"));
    }

    @Test
    void gaugeShouldTrackStateObject() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        long[] state = {7L};

        Gauge gauge = MetricsSupport.gauge(registry, "cache", "entries", state, value -> value[0]);

        assertThat(gauge.value()).isEqualTo(7.0);
        state[0] = 9L;
        assertThat(registry.get("ypbin.cache.entries").gauge().value()).isEqualTo(9.0);
        assertThat(registry.get("ypbin.cache.entries").gauge().getId().getTag("module")).isEqualTo("cache");
    }

    @Test
    void counterAndGaugeShouldRejectNullRegistryOrState() {
        assertThatThrownBy(() -> MetricsSupport.counter(null, "cache", "requests"))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> MetricsSupport.increment(null, "cache", "requests"))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> MetricsSupport.gauge(new SimpleMeterRegistry(), "cache", "entries", null,
            value -> 0.0)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> MetricsSupport.gauge(new SimpleMeterRegistry(), "cache", "entries", new Object(),
            null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> MetricsSupport.gauge(null, "cache", "entries", new Object(), value -> 0.0))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void autoDiscoveredMeterNamesShouldAllUsePrefix() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MetricsSupport.increment(registry, "cache", "requests", "level", "l1", "result", "hit");
        MetricsSupport.gauge(registry, "lock", "pending", new Object(), value -> 1.0);

        List<String> names = registry.getMeters().stream().map(meter -> meter.getId().getName()).toList();
        assertThat(names).allMatch(name -> name.startsWith(MetricNames.PREFIX + "."));
    }
}

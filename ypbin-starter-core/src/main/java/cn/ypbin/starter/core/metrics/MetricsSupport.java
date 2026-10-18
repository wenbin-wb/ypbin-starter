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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;

/**
 * Micrometer 埋点薄封装：统一走 {@link MetricNames} 的命名与标签约定，并<b>自动补
 * {@value MetricNames#TAG_MODULE} 标签</b>。
 *
 * <p>解决的问题是宿主里反复出现的 <code>registry.counter(PREFIX + "xxx")</code> 手拼：
 * 手拼容易漏前缀、标签键写错（{@code type} / {@code typo} 各写一份，指标在后台被悄悄拆成两条），
 * 且每个宿主都要自己发明一套。用本类调用即自动获得「{@code ypbin.<module>.<name>} + module 标签」。</p>
 *
 * <p><b>标签顺序稳定</b>：标签按 key 字典序排序后再交给 Micrometer，同样一组标签无论调用方
 * 传参顺序如何都产出同一个 {@code Meter.Id}（Micrometer 自身对同 id 幂等，但标签顺序不稳定会让
 * 序列化/断言/日志里的形态抖动）。重复 key、空 key、保留 key {@code module} 一律拒绝。</p>
 *
 * <p><b>可选依赖</b>：micrometer-core 在 core 模块是 {@code optional}，不引入 micrometer 的宿主
 * 不会传递到本类依赖；需要埋点时自行引入 micrometer（Spring Boot Actuator 自带）。
 * 本类不是自动配置的一部分，无 micrometer 时根本不会被加载。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
public final class MetricsSupport {

    private MetricsSupport() {
    }

    /**
     * 注册（或取回已注册的）计数器并自增 1。
     *
     * @param registry 指标注册表
     * @param module   模块段（作为 {@code module} 标签值，同时用于指标名的模块段）
     * @param name     指标名（不含前缀）
     * @param tagPairs 额外标签，按 {@code key, value, key, value...} 传入
     * @return 自增后的计数器
     */
    public static Counter increment(MeterRegistry registry, String module, String name, String... tagPairs) {
        Counter counter = counter(registry, module, name, tagPairs);
        counter.increment();
        return counter;
    }

    /**
     * 注册（或取回已注册的）计数器。
     *
     * @param registry 指标注册表
     * @param module   模块段（自动补 {@code module} 标签，值同名）
     * @param name     指标名（不含前缀）
     * @param tagPairs 额外标签，按 {@code key, value, key, value...} 传入
     * @return 计数器
     */
    public static Counter counter(MeterRegistry registry, String module, String name, String... tagPairs) {
        Objects.requireNonNull(registry, "registry");
        String metricName = MetricNames.of(module, name);
        return Counter.builder(metricName)
            .description(metricName)
            .tags(tags(module, tagPairs))
            .register(registry);
    }

    /**
     * 注册（或取回已注册的）数值 Gauge。
     *
     * @param registry      指标注册表
     * @param module        模块段（自动补 {@code module} 标签）
     * @param name          指标名（不含前缀）
     * @param stateObject   被观测对象（Micrometer 弱引用持有，需由调用方保证生命周期）
     * @param valueFunction 从被观测对象取值
     * @param tagPairs      额外标签
     * @param <T>           被观测对象类型
     * @return Gauge
     */
    public static <T> Gauge gauge(MeterRegistry registry, String module, String name, T stateObject,
            ToDoubleFunction<T> valueFunction, String... tagPairs) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(stateObject, "stateObject");
        Objects.requireNonNull(valueFunction, "valueFunction");
        String metricName = MetricNames.of(module, name);
        return Gauge.builder(metricName, stateObject, valueFunction::applyAsDouble)
            .description(metricName)
            .tags(tags(module, tagPairs))
            .register(registry);
    }

    /**
     * 构造标签集合：固定补 {@code module}，其余按 key 字典序排序，保证同组标签的 {@code Meter.Id} 稳定。
     *
     * @param module   模块名（{@code module} 标签值）
     * @param tagPairs {@code key, value, ...} 形式的额外标签
     * @return Micrometer 标签集合
     */
    public static Tags tags(String module, String... tagPairs) {
        String moduleValue = MetricNames.requireSegment(module, "module");
        if (tagPairs == null || tagPairs.length == 0) {
            return Tags.of(MetricNames.TAG_MODULE, moduleValue);
        }
        if (tagPairs.length % 2 != 0) {
            throw new IllegalArgumentException("标签必须成对传入（key, value），实际个数: " + tagPairs.length);
        }
        // TreeMap 去重 + 排序：同 key 重复属调用方 bug，显式拒绝而不是让后值静默覆盖前值
        TreeMap<String, String> sorted = new TreeMap<>();
        for (int index = 0; index < tagPairs.length; index += 2) {
            String key = tagPairs[index];
            String value = tagPairs[index + 1];
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("标签 key 不能为空（位置 " + index + "）");
            }
            if (value == null) {
                throw new IllegalArgumentException("标签 value 不能为 null: " + key);
            }
            if (MetricNames.TAG_MODULE.equals(key)) {
                throw new IllegalArgumentException("标签 key " + MetricNames.TAG_MODULE
                    + " 由本方法自动补齐，请勿重复传入");
            }
            if (sorted.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("标签 key 重复: " + key);
            }
        }
        Tags tags = Tags.of(MetricNames.TAG_MODULE, moduleValue);
        for (var entry : sorted.entrySet()) {
            tags = tags.and(entry.getKey(), entry.getValue());
        }
        return tags;
    }
}

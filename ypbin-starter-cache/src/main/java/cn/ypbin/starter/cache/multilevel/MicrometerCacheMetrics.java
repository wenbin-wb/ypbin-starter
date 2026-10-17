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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 基于 Micrometer 的多级缓存指标实现。
 *
 * <p><strong>指标定义</strong>（均带 {@code cache} 标签，值为调用方传入的缓存标识）：</p>
 * <ul>
 *     <li>{@code ypbin.cache.requests}：读请求计数，标签 {@code level=l1|l2} + {@code result=hit|miss}；</li>
 *     <li>{@code ypbin.cache.null.sentinel.hits}：被 L1 空值哨兵拦截（防穿透阻断）次数；</li>
 *     <li>{@code ypbin.cache.loads}：触发 L2 {@code getOrLoad} 装载的次数。</li>
 * </ul>
 *
 * <p>接入方可据此算命中率（{@code hit / (hit + miss)}）并对「哨兵拦截持续高位 + L2 miss 高位」设告警，
 * 用于判断缓存击穿/穿透压力是否超出预期。</p>
 *
 * <p>{@code cache} 标签值由配置项
 * {@code ypbin.cache.multi-level.metrics-cache-name} 决定（生产装配见
 * {@code MultiLevelCacheAutoConfiguration.CacheMetricsConfiguration}）：默认
 * {@link MultiLevelCacheProperties#DEFAULT_METRICS_CACHE_NAME}（{@code multi-level}，多副本聚合为同一序列，
 * 与历史行为一致）；多实例部署建议配置为实例名（如 {@code ${HOSTNAME}}），使各副本的指标序列可按
 * {@code cache} 标签下钻区分。</p>
 *
 * <p>本类引用 Micrometer 类型，故只在 {@code MeterRegistry} 存在时被装配（见
 * {@code MultiLevelCacheAutoConfiguration.CacheMetricsConfiguration} 的类级 {@code @ConditionalOnClass}）。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
public final class MicrometerCacheMetrics implements CacheMetricsRecorder {

    /** 读请求计数指标名 */
    static final String REQUESTS_METRIC = "ypbin.cache.requests";

    /** 空值哨兵（防穿透）拦截计数指标名 */
    static final String NULL_SENTINEL_METRIC = "ypbin.cache.null.sentinel.hits";

    /** L2 装载调用计数指标名 */
    static final String LOADS_METRIC = "ypbin.cache.loads";

    private final Counter l1Hits;

    private final Counter l1Misses;

    private final Counter l2Hits;

    private final Counter l2Misses;

    private final Counter nullSentinelHits;

    private final Counter loads;

    private MicrometerCacheMetrics(Counter l1Hits, Counter l1Misses, Counter l2Hits, Counter l2Misses,
            Counter nullSentinelHits, Counter loads) {
        this.l1Hits = l1Hits;
        this.l1Misses = l1Misses;
        this.l2Hits = l2Hits;
        this.l2Misses = l2Misses;
        this.nullSentinelHits = nullSentinelHits;
        this.loads = loads;
    }

    /**
     * 注册并返回指标实现（重复调用返回同一组已注册计数器，Micrometer 自身保证幂等）。
     *
     * @param registry  MeterRegistry
     * @param cacheName {@code cache} 标签值；生产装配传
     *                  {@link MultiLevelCacheProperties#getMetricsCacheName()}（默认
     *                  {@link MultiLevelCacheProperties#DEFAULT_METRICS_CACHE_NAME}，多实例建议配为实例名）
     * @return 指标实现
     */
    public static MicrometerCacheMetrics of(MeterRegistry registry, String cacheName) {
        return new MicrometerCacheMetrics(
            requests(registry, cacheName, "l1", "hit"),
            requests(registry, cacheName, "l1", "miss"),
            requests(registry, cacheName, "l2", "hit"),
            requests(registry, cacheName, "l2", "miss"),
            Counter.builder(NULL_SENTINEL_METRIC)
                .tag("cache", cacheName)
                .description("多级缓存 L1 空值哨兵拦截次数（防穿透阻断）")
                .register(registry),
            Counter.builder(LOADS_METRIC)
                .tag("cache", cacheName)
                .description("多级缓存触发 L2 getOrLoad 装载的次数")
                .register(registry));
    }

    private static Counter requests(MeterRegistry registry, String cacheName, String level, String result) {
        return Counter.builder(REQUESTS_METRIC)
            .tag("cache", cacheName)
            .tag("level", level)
            .tag("result", result)
            .description("多级缓存读请求次数")
            .register(registry);
    }

    @Override
    public void l1Hit() {
        l1Hits.increment();
    }

    @Override
    public void l1Miss() {
        l1Misses.increment();
    }

    @Override
    public void l2Hit() {
        l2Hits.increment();
    }

    @Override
    public void l2Miss() {
        l2Misses.increment();
    }

    @Override
    public void nullSentinelHit() {
        nullSentinelHits.increment();
    }

    @Override
    public void load() {
        loads.increment();
    }
}

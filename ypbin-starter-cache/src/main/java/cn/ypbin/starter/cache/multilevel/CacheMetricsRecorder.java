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

/**
 * 多级缓存运行时指标记录器。
 *
 * <p><strong>为什么是接口而不是直接依赖 Micrometer</strong>：{@code micrometer-core} 在本模块是
 * <em>optional</em> 依赖，宿主可能完全不引入。{@link MultiLevelCacheService} 只依赖本接口，Micrometer 的
 * 字节码全部隔离在 {@link MicrometerCacheMetrics} 里——避免"类校验为字段/局部变量加载 Micrometer 类型"
 * 导致未引入依赖时连缓存本身都起不来。</p>
 *
 * <p>未引入 Micrometer（或未注册 {@code MeterRegistry}）时装配 {@link #NOOP}，零开销、无副作用。</p>
 *
 * <p><strong>实现约束：任何实现都不得抛异常。</strong>{@link MultiLevelCacheService} 在读写缓存的主路径上
 * 直接调用本接口，埋点异常会<strong>改变缓存调用语义</strong>——本该返回缓存值/回源结果的调用变成抛异常，
 * 本该成功的写入被中断。指标是观测能力、不是业务前置条件，因此实现内部必须自行兜底（或降级为 no-op），
 * 不得把指标后端的故障传播给缓存调用方。</p>
 *
 * <p>为防第三方实现违约，{@link MultiLevelCacheService} 还会把传入的实现套上
 * {@link SafeCacheMetricsRecorder}：运行时异常被记录日志后隔离、{@code Error} 原样抛出。这是兜底而非许可——
 * 实现仍应保证自身不抛异常。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
public interface CacheMetricsRecorder {

    /** L1（本地 Caffeine）命中一次（含空值哨兵命中）。 */
    void l1Hit();

    /** L1 未命中一次。 */
    void l1Miss();

    /** L2（分布式）命中一次。 */
    void l2Hit();

    /** L2 未命中一次。 */
    void l2Miss();

    /**
     * 请求被 L1 的<strong>空值哨兵</strong>拦截一次，即「防穿透阻断」计数。
     *
     * <p>它与 {@link #l1Hit()} 同时发生：命中哨兵同样是本地命中，但结果为 {@code null}。</p>
     */
    void nullSentinelHit();

    /**
     * 触发一次 L2 {@code getOrLoad} 装载调用。
     *
     * <p>该计数包含「L2 命中」与「真实回源」两种情形——{@code getOrLoad} 的内部行为对调用方不可见，
     * 故只表述为装载调用量；用于观测回源路径是否被高频触发。</p>
     */
    void load();

    /** 无指标后端时的空实现（默认装配）。 */
    CacheMetricsRecorder NOOP = new CacheMetricsRecorder() {

        @Override
        public void l1Hit() {
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
    };
}

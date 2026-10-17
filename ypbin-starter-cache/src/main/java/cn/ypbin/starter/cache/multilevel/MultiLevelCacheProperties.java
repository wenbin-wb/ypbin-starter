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

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 多级缓存配置项。
 *
 * @author wenbin
 * @since 2026-07-31
 */
@ConfigurationProperties(prefix = MultiLevelCacheProperties.PREFIX)
public class MultiLevelCacheProperties {

    public static final String PREFIX = "ypbin.cache.multi-level";

    /**
     * 多级缓存指标的 {@code cache} 标签默认值。
     *
     * <p>刻意保持「与实例无关的固定标识」：多副本未显式配置时聚合为同一条指标序列，
     * 与历史上写死 {@code multi-level} 的行为完全一致。</p>
     */
    public static final String DEFAULT_METRICS_CACHE_NAME = "multi-level";

    /** 是否启用多级缓存（L1 本地 + L2 Redis），默认关闭 */
    private boolean enabled = false;

    /** L1 本地缓存最大条目数 */
    private long localMaxSize = 10_000L;

    /** L1 本地缓存写后过期秒数（应小于 L2 TTL，作为最终一致兜底） */
    private long localExpireSeconds = 300L;

    /** 是否开启跨实例失效广播（多副本部署需开启，单体可关） */
    private boolean invalidationBroadcast = true;

    /** 失效广播频道名 */
    private String invalidationChannel = "ypbin:cache:invalidation";

    /**
     * 多级缓存运行时指标的 {@code cache} 标签值，默认 {@link #DEFAULT_METRICS_CACHE_NAME}。
     *
     * <p>多副本部署时，各实例默认产生<strong>相同</strong>的 {@code cache} 标签，指标聚合后无法区分是哪台
     * 机器的缓存异常。<strong>多实例部署建议配置为实例名</strong>，例如
     * {@code ypbin.cache.multi-level.metrics-cache-name=${HOSTNAME}}（K8s 中 {@code HOSTNAME} 即 Pod 名），
     * 或 {@code ${spring.application.name}:${HOSTNAME}} 以同时保留应用维度；配置后 {@code cache} 标签即改用
     * 新值，可据其按实例下钻（配合失效广播排查「某实例 L1 未摘除」一类问题）。</p>
     *
     * <p>配置值支持 Spring 占位符（{@code ${...}}）：绑定阶段就由宿主 {@code Environment} 解析，
     * 因此无需本模块感知实例身份。</p>
     */
    private String metricsCacheName = DEFAULT_METRICS_CACHE_NAME;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getLocalMaxSize() {
        return localMaxSize;
    }

    public void setLocalMaxSize(long localMaxSize) {
        this.localMaxSize = localMaxSize;
    }

    public long getLocalExpireSeconds() {
        return localExpireSeconds;
    }

    public void setLocalExpireSeconds(long localExpireSeconds) {
        this.localExpireSeconds = localExpireSeconds;
    }

    public boolean isInvalidationBroadcast() {
        return invalidationBroadcast;
    }

    public void setInvalidationBroadcast(boolean invalidationBroadcast) {
        this.invalidationBroadcast = invalidationBroadcast;
    }

    public String getInvalidationChannel() {
        return invalidationChannel;
    }

    public void setInvalidationChannel(String invalidationChannel) {
        this.invalidationChannel = invalidationChannel;
    }

    public String getMetricsCacheName() {
        return metricsCacheName;
    }

    /**
     * 设置指标 {@code cache} 标签值。
     *
     * <p>拒绝空白值：空白标签会让所有指标序列失去区分度（等于回到「无法按实例下钻」的状态），
     * 属于典型的手滑配置，宁可启动即失败也不静默产出不可用的指标。</p>
     *
     * @param metricsCacheName 标签值（非空白）
     */
    public void setMetricsCacheName(String metricsCacheName) {
        String value = Objects.requireNonNull(metricsCacheName, "metricsCacheName 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                PREFIX + ".metrics-cache-name 不能为空白：空白标签值会让多级缓存指标序列失去区分度");
        }
        this.metricsCacheName = value;
    }
}

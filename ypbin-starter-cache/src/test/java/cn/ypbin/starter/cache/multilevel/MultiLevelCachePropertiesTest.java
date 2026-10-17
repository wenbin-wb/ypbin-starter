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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link MultiLevelCacheProperties} 配置项测试：锁定指标 {@code cache} 标签的默认值（必须与历史行为一致）
 * 与非法值（空白）的快速失败语义。
 *
 * @author wenbin
 * @since 2026-10-17
 */
class MultiLevelCachePropertiesTest {

    @Test
    @DisplayName("默认 metricsCacheName 为 multi-level：未配置时行为与历史常量一致")
    void defaultMetricsCacheNameIsMultiLevel() {
        MultiLevelCacheProperties properties = new MultiLevelCacheProperties();

        assertThat(properties.getMetricsCacheName()).isEqualTo(MultiLevelCacheProperties.DEFAULT_METRICS_CACHE_NAME);
        assertThat(properties.getMetricsCacheName())
            .as("默认值是对外契约（Prometheus 查询按 cache=\"multi-level\" 写死），不得悄悄改变")
            .isEqualTo("multi-level");
    }

    @Test
    @DisplayName("可覆盖为任意实例名")
    void metricsCacheNameIsConfigurable() {
        MultiLevelCacheProperties properties = new MultiLevelCacheProperties();

        properties.setMetricsCacheName("${HOSTNAME}");

        assertThat(properties.getMetricsCacheName()).isEqualTo("${HOSTNAME}");
    }

    @Test
    @DisplayName("空白值快速失败：空白标签会让指标序列失去区分度，不得静默放行")
    void blankMetricsCacheNameIsRejected() {
        MultiLevelCacheProperties properties = new MultiLevelCacheProperties();

        assertThatThrownBy(() -> properties.setMetricsCacheName("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(MultiLevelCacheProperties.PREFIX + ".metrics-cache-name");
        assertThatThrownBy(() -> properties.setMetricsCacheName(null))
            .isInstanceOf(NullPointerException.class);
        // 失败后默认值保持不变，不产生半更新状态
        assertThat(properties.getMetricsCacheName()).isEqualTo(MultiLevelCacheProperties.DEFAULT_METRICS_CACHE_NAME);
    }
}

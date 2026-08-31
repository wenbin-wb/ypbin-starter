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
package cn.ypbin.starter.observability.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 可观测性配置测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class ObservabilityTest {

    @Test
    void propertiesShouldExposeDefaults() {
        ObservabilityProperties props = new ObservabilityProperties();
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getRequestIdHeader()).isEqualTo("X-Request-Id");
        assertThat(props.getMdcKey()).isEqualTo("requestId");
        props.setEnabled(false);
        props.setRequestIdHeader("X-Trace");
        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getRequestIdHeader()).isEqualTo("X-Trace");
    }

    @Test
    void autoConfigurationShouldBuildFilter() {
        ObservabilityAutoConfiguration config = new ObservabilityAutoConfiguration();
        ObservabilityProperties props = new ObservabilityProperties();
        assertThat(config.requestIdMdcFilterRegistration(props)).isNotNull();
    }
}

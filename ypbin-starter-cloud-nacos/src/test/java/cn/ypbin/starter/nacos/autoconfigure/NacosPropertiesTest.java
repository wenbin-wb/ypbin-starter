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
package cn.ypbin.starter.nacos.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Nacos 配置属性测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class NacosPropertiesTest {

    @Test
    void shouldExposeDefaults() {
        NacosProperties props = new NacosProperties();
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.isDefaultProfileEnabled()).isTrue();
        assertThat(props.getDefaultProfile()).isEqualTo("dev");
        assertThat(props.isConfigImportEnabled()).isTrue();
    }

    @Test
    void shouldAllowOverride() {
        NacosProperties props = new NacosProperties();
        props.setEnabled(false);
        props.setDefaultProfile("prod");
        props.setServiceVersion("1.0.0");
        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getDefaultProfile()).isEqualTo("prod");
        assertThat(props.getServiceVersion()).isEqualTo("1.0.0");
        props.setConfigImportEnabled(false);
        props.setFailOnMultiplePresetProfiles(false);
        props.setApplicationName("demo");
        assertThat(props.isConfigImportEnabled()).isFalse();
        assertThat(props.isFailOnMultiplePresetProfiles()).isFalse();
        assertThat(props.getApplicationName()).isEqualTo("demo");
    }
}

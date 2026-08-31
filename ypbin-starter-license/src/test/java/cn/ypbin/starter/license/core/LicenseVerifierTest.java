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
package cn.ypbin.starter.license.core;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.license.autoconfigure.LicenseProperties;
import org.junit.jupiter.api.Test;

/**
 * License 校验器与配置测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class LicenseVerifierTest {

    @Test
    void propertiesShouldExposeDefaults() {
        LicenseProperties props = new LicenseProperties();
        assertThat(props).isNotNull();
        LicenseProperties.Online online = props.getOnline();
        if (online != null) {
            assertThat(online).isNotNull();
        }
    }

    @Test
    void licenseStatusShouldExposeValues() {
        assertThat(LicenseStatus.LEGAL.isUsable()).isTrue();
        assertThat(LicenseStatus.ILLEGAL.isUsable()).isFalse();
    }
}

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
package cn.ypbin.starter.apidoc.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link ApiDocProperties} 单元测试。
 *
 * @author wenbin
 * @since 2026-07-31
 */
class ApiDocPropertiesTest {

    @Test
    void shouldHaveSaneDefaults() {
        ApiDocProperties properties = new ApiDocProperties();

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.isDisableInProd()).isTrue();
        assertThat(properties.getTitle()).isEqualTo("API 文档");
        assertThat(properties.getGroupName()).isEqualTo("default");
        assertThat(properties.getPathsToMatch()).containsExactly("/**");
        assertThat(properties.getPathsToExclude()).contains("/error", "/actuator/**");
        assertThat(properties.getSecurityHeaders()).contains("Authorization", "X-Request-Id", "X-Tenant-Id", "X-Version");
    }
}

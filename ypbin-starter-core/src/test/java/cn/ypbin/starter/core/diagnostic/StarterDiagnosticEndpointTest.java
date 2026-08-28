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
package cn.ypbin.starter.core.diagnostic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

/**
 * {@link StarterDiagnosticEndpoint} 单元测试。
 *
 * @author wenbin
 * @since 2026-08-28
 */
class StarterDiagnosticEndpointTest {

    @Test
    void diagnosticInfo_returnsExpectedMetadata() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.refresh();

        StarterDiagnosticEndpoint endpoint = new StarterDiagnosticEndpoint(context);
        Map<String, Object> info = endpoint.diagnosticInfo();

        assertThat(info).isNotNull();
        assertThat(info.get("framework")).isEqualTo("ypbin-starter");
        assertThat(info.get("version")).isNotNull();
        assertThat(info.get("javaVersion")).isNotNull();
        assertThat(info.get("springBootVersion")).isNotNull();

        assertThat(info.get("activeAutoConfigurations")).isInstanceOf(List.class);
        assertThat(info.get("activeConfigurationCount")).isInstanceOf(Integer.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> features = (Map<String, Object>) info.get("features");
        assertThat(features).isNotNull();
        assertThat(features).containsKey("security");
        assertThat(features).containsKey("ai");
        assertThat(features).containsKey("excel");

        context.close();
    }
}

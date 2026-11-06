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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

/**
 * {@link StarterDiagnosticEndpoint} 单元测试。
 *
 * @author wenbin
 * @since 2026-08-28
 */
class StarterDiagnosticEndpointTest {

    /** 与主代码同一份打包元数据：测试 classpath 里由资源过滤填充为 ${project.version} 的实际值。 */
    private static String expectedVersion() {
        Properties properties = new Properties();
        try (InputStream input = StarterDiagnosticEndpointTest.class.getClassLoader()
                .getResourceAsStream("META-INF/ypbin-starter.properties")) {
            assertThat(input).as("打包元数据 META-INF/ypbin-starter.properties 应存在").isNotNull();
            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        String version = properties.getProperty("version");
        assertThat(version).as("版本资源必须被 Maven 资源过滤填充").doesNotStartWith("${");
        return version;
    }

    @Test
    void diagnosticInfo_returnsExpectedMetadata() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.refresh();

        StarterDiagnosticEndpoint endpoint = new StarterDiagnosticEndpoint(context);
        Map<String, Object> info = endpoint.diagnosticInfo();

        assertThat(info).isNotNull();
        assertThat(info.get("framework")).isEqualTo("ypbin-starter");
        // 版本必须来自打包元数据（META-INF/ypbin-starter.properties）或清单，绝不回落到历史常量
        assertThat(info.get("version")).isNotEqualTo("1.4.0");
        assertThat(info.get("version")).isEqualTo(expectedVersion());
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

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
package cn.ypbin.starter.apidoc;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.apidoc.autoconfigure.ApiDocAutoConfiguration;
import cn.ypbin.starter.apidoc.autoconfigure.ApiDocDefaultsEnvironmentPostProcessor;
import cn.ypbin.starter.apidoc.autoconfigure.ApiDocProperties;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

/**
 * API 文档自动装配与默认环境后置处理测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class ApiDocAutoConfigurationTest {

    @Test
    void shouldBuildOpenApiFromProperties() {
        ApiDocProperties props = new ApiDocProperties();
        props.setTitle("测试 API");
        props.setDescription("描述");
        props.setVersion("1.0");
        ApiDocAutoConfiguration config = new ApiDocAutoConfiguration();
        OpenAPI openApi = config.ypbinOpenAPI(props);
        assertThat(openApi).isNotNull();
        assertThat(openApi.getInfo()).isNotNull();
    }

    @Test
    void shouldBuildGroupedOpenApi() {
        ApiDocProperties props = new ApiDocProperties();
        ApiDocAutoConfiguration config = new ApiDocAutoConfiguration();
        assertThat(config.ypbinDefaultGroupedOpenApi(props)).isNotNull();
        assertThat(config.ypbinApiDocOperationCustomizer(props)).isNotNull();
    }

    @Test
    void webConfigurerShouldBeProvided() {
        ApiDocAutoConfiguration config = new ApiDocAutoConfiguration();
        io.swagger.v3.oas.models.OpenAPI openApi = new io.swagger.v3.oas.models.OpenAPI();
        config.ypbinApiDocOpenApiCustomizer().customise(openApi);
        assertThat(openApi).isNotNull();
    }

    @Test
    void propertiesNestedShouldExposeContactAndLicense() {
        ApiDocProperties props = new ApiDocProperties();
        ApiDocProperties.Contact contact = props.getContact();
        ApiDocProperties.License license = props.getLicense();
        assertThat(contact).isNotNull();
        assertThat(license).isNotNull();
    }

    @Test
    void defaultsShouldDisableInProd() {
        ApiDocDefaultsEnvironmentPostProcessor processor = new ApiDocDefaultsEnvironmentPostProcessor();
        org.springframework.core.env.StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("prod");
        processor.postProcessEnvironment(env, null);
        assertThat(env.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
    }

    @Test
    void defaultsShouldKeepEnabledInDev() {
        ApiDocDefaultsEnvironmentPostProcessor processor = new ApiDocDefaultsEnvironmentPostProcessor();
        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles("dev");
        processor.postProcessEnvironment(env, null);
        assertThat(env.getProperty("springdoc.api-docs.enabled")).isNotEqualTo("false");
    }

    @Test
    void propertiesShouldExposeDefaults() {
        ApiDocProperties props = new ApiDocProperties();
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getTitle()).isNotBlank();
    }
}

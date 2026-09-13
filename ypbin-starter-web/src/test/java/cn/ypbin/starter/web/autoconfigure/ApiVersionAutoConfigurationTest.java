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
package cn.ypbin.starter.web.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * {@link ApiVersionAutoConfiguration} 测试：默认关闭、开启后注册版本化 WebMvc 配置，
 * 且三种解析方式均能正确装配。
 *
 * @author wenbin
 * @since 2026-09-13
 */
class ApiVersionAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ApiVersionAutoConfiguration.class));

    @Test
    void shouldStayDisabledByDefault() {
        runner.run(context -> assertThat(context).doesNotHaveBean(WebMvcConfigurer.class));
    }

    @Test
    void shouldRegisterConfigurerWhenEnabled() {
        runner.withPropertyValues("ypbin.web.api-version.enabled=true")
            .run(context -> assertThat(context).hasSingleBean(WebMvcConfigurer.class));
    }

    @Test
    void propertiesShouldExposeSensibleDefaults() {
        ApiVersionProperties properties = new ApiVersionProperties();
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getResolver()).isEqualTo(ApiVersionProperties.Resolver.HEADER);
        assertThat(properties.getHeaderName()).isEqualTo("X-Api-Version");
        assertThat(properties.getQueryParam()).isEqualTo("version");
        assertThat(properties.getPathSegmentIndex()).isEqualTo(1);
        assertThat(properties.isVersionRequired()).isFalse();
        assertThat(properties.getDefaultVersion()).isEqualTo("1.0");
        assertThat(properties.getSupportedVersions()).isEmpty();
    }

    @Test
    void propertiesShouldBeMutable() {
        ApiVersionProperties properties = new ApiVersionProperties();
        properties.setEnabled(true);
        properties.setResolver(ApiVersionProperties.Resolver.PATH_SEGMENT);
        properties.setHeaderName("X-V");
        properties.setQueryParam("v");
        properties.setPathSegmentIndex(2);
        properties.setVersionRequired(true);
        properties.setDefaultVersion("2.0");
        properties.setSupportedVersions(List.of("1.0", "2.0"));

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getResolver()).isEqualTo(ApiVersionProperties.Resolver.PATH_SEGMENT);
        assertThat(properties.getHeaderName()).isEqualTo("X-V");
        assertThat(properties.getQueryParam()).isEqualTo("v");
        assertThat(properties.getPathSegmentIndex()).isEqualTo(2);
        assertThat(properties.isVersionRequired()).isTrue();
        assertThat(properties.getDefaultVersion()).isEqualTo("2.0");
        assertThat(properties.getSupportedVersions()).containsExactly("1.0", "2.0");
    }

    @Test
    void shouldConfigureEachResolverWithoutError() {
        // 三种解析方式与「显式版本清单 / 自动探测」两条分支均需可配置
        assertThatCode(() -> configure(ApiVersionProperties.Resolver.HEADER, List.of()))
            .doesNotThrowAnyException();
        assertThatCode(() -> configure(ApiVersionProperties.Resolver.QUERY_PARAM, List.of("1.0")))
            .doesNotThrowAnyException();
        assertThatCode(() -> configure(ApiVersionProperties.Resolver.PATH_SEGMENT, List.of("1.0")))
            .doesNotThrowAnyException();
    }

    private static void configure(ApiVersionProperties.Resolver resolver, List<String> supported) {
        ApiVersionProperties properties = new ApiVersionProperties();
        properties.setResolver(resolver);
        properties.setSupportedVersions(supported);
        WebMvcConfigurer configurer =
            new ApiVersionAutoConfiguration().ypbinApiVersionWebMvcConfigurer(properties);
        ApiVersionConfigurer apiVersionConfigurer = new ApiVersionConfigurer();
        configurer.configureApiVersioning(apiVersionConfigurer);
        assertThat(apiVersionConfigurer).isNotNull();
    }
}

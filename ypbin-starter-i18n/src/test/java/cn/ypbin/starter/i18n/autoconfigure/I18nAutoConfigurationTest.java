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
package cn.ypbin.starter.i18n.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.i18n.core.I18nUtilInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

/**
 * 国际化自动装配测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class I18nAutoConfigurationTest {

    @Test
    void shouldBuildInitializer() {
        I18nAutoConfiguration config = new I18nAutoConfiguration();
        I18nUtilInitializer initializer = config.i18nUtilInitializer(new StaticMessageSource());
        assertThat(initializer).isNotNull();
    }

    @Test
    void shouldBuildLocaleResolver() {
        I18nAutoConfiguration config = new I18nAutoConfiguration();
        I18nProperties props = new I18nProperties();
        assertThat(config.localeResolver(props)).isNotNull();
    }
}

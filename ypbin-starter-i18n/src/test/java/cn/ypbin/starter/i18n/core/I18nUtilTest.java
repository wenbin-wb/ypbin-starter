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
package cn.ypbin.starter.i18n.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.ypbin.starter.i18n.autoconfigure.I18nProperties;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

/**
 * 国际化工具与配置测试。
 *
 * @author wenbin
 * @since 2026-08-31
 */
class I18nUtilTest {

    @Test
    void messageShouldReturnCodeWhenSourceAbsent() throws Exception {
        clearSource();
        assertThat(I18nUtil.message("some.code")).isEqualTo("some.code");
    }

    @Test
    void messageShouldDelegateToSource() throws Exception {
        clearSource();
        MessageSource source = mock(MessageSource.class);
        when(source.getMessage(org.mockito.ArgumentMatchers.eq("hello"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("hello"), org.mockito.ArgumentMatchers.eq(Locale.ENGLISH))).thenReturn("Hello");
        I18nUtil.setMessageSource(source);
        assertThat(I18nUtil.message("hello", Locale.ENGLISH)).isEqualTo("Hello");
    }

    private void clearSource() throws Exception {
        java.lang.reflect.Field field = I18nUtil.class.getDeclaredField("messageSource");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test
    void propertiesShouldExposeDefaults() {
        I18nProperties props = new I18nProperties();
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getParamName()).isEqualTo("lang");
        assertThat(props.getHeaderName()).isEqualTo("Accept-Language");
        assertThat(props.getDefaultLocale()).isEqualTo("zh_CN");
        assertThat(I18nProperties.PREFIX).isEqualTo("ypbin.i18n");
    }
}

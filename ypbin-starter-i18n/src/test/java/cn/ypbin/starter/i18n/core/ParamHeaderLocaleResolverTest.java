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

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * {@link ParamHeaderLocaleResolver} 单元测试：参数优先、请求头回退、默认回退与多写法归一。
 *
 * @author wenbin
 * @since 2026-08-05
 */
class ParamHeaderLocaleResolverTest {

    private static final Locale DEFAULT = Locale.CHINA;

    private final ParamHeaderLocaleResolver resolver =
        new ParamHeaderLocaleResolver("lang", "Accept-Language", DEFAULT);

    @Test
    void resolve_shouldPreferParamOverHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("lang", "en_US");
        request.addHeader("Accept-Language", "ja_JP");

        Locale locale = resolver.resolveLocale(request);

        assertThat(locale).isEqualTo(new Locale("en", "US"));
    }

    @Test
    void resolve_shouldFallBackToHeaderWhenNoParam() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "en_US");

        assertThat(resolver.resolveLocale(request)).isEqualTo(new Locale("en", "US"));
    }

    @Test
    void resolve_shouldFallBackToDefaultWhenNothingProvided() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(resolver.resolveLocale(request)).isEqualTo(DEFAULT);
    }

    @Test
    void resolve_shouldAcceptBothUnderscoreAndHyphen() {
        MockHttpServletRequest underscore = new MockHttpServletRequest();
        underscore.setParameter("lang", "zh_CN");
        MockHttpServletRequest hyphen = new MockHttpServletRequest();
        hyphen.setParameter("lang", "zh-CN");

        assertThat(resolver.resolveLocale(underscore)).isEqualTo(new Locale("zh", "CN"));
        assertThat(resolver.resolveLocale(hyphen)).isEqualTo(new Locale("zh", "CN"));
    }

    @Test
    void resolve_shouldTakeFirstLanguageFromWeightedAcceptLanguage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8");

        // 取首选语言，忽略权重与其余候选
        assertThat(resolver.resolveLocale(request)).isEqualTo(new Locale("en", "US"));
    }

    @Test
    void resolve_withBlankParam_shouldFallBackToHeaderThenDefault() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("lang", "   ");

        assertThat(resolver.resolveLocale(request)).isEqualTo(DEFAULT);
    }
}

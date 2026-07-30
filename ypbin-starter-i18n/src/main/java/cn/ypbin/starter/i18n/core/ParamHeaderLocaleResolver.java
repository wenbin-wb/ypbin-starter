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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.LocaleResolver;

/**
 * 参数 + 请求头 Locale 解析器。
 *
 * <p>优先取请求参数（如 {@code ?lang=en_US}），其次取请求头（默认 {@code Accept-Language}），
 * 都没有则回退默认语言。参数值支持 {@code zh_CN}（下划线）或 {@code zh-CN}（连字符）两种写法。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class ParamHeaderLocaleResolver implements LocaleResolver {

    private final String paramName;
    private final String headerName;
    private final Locale defaultLocale;

    public ParamHeaderLocaleResolver(String paramName, String headerName, Locale defaultLocale) {
        this.paramName = paramName;
        this.headerName = headerName;
        this.defaultLocale = defaultLocale;
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        String value = request.getParameter(paramName);
        if (!StringUtils.hasText(value)) {
            value = request.getHeader(headerName);
        }
        if (!StringUtils.hasText(value)) {
            return defaultLocale;
        }
        // 取首选语言（Accept-Language 可能带权重与多值）
        String first = value.split(",")[0].trim().split(";")[0].trim();
        Locale locale = Locale.forLanguageTag(first.replace('_', '-'));
        return (locale == null || locale.getLanguage().isEmpty()) ? defaultLocale : locale;
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        // 无状态：语言由每次请求的参数/头决定，不做服务端存储
    }
}

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

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

/**
 * 国际化工具。
 *
 * <p>静态方法按当前请求 Locale 翻译消息码，业务方无需注入 MessageSource。Locale 取自
 * {@link LocaleContextHolder}（由 Locale 解析器按请求头/参数设置）。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public final class I18nUtil {

    /** volatile：装配时机与读取可能跨线程（如异步请求），保证可见性 */
    private static volatile MessageSource messageSource;

    private I18nUtil() {
    }

    static void setMessageSource(MessageSource messageSource) {
        I18nUtil.messageSource = messageSource;
    }

    /**
     * 按当前 Locale 翻译消息码。
     *
     * @param code 消息码
     * @param args 占位参数
     * @return 翻译文本；无对应消息时返回消息码本身
     */
    public static String message(String code, Object... args) {
        return message(code, LocaleContextHolder.getLocale(), args);
    }

    /**
     * 按指定 Locale 翻译消息码。
     *
     * @param code   消息码
     * @param locale 语言
     * @param args   占位参数
     * @return 翻译文本；无对应消息时返回消息码本身
     */
    public static String message(String code, Locale locale, Object... args) {
        if (messageSource == null) {
            return code;
        }
        return messageSource.getMessage(code, args, code, locale);
    }
}

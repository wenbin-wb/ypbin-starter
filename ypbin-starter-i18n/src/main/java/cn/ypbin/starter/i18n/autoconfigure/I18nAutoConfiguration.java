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

import cn.ypbin.starter.i18n.core.I18nUtilInitializer;
import cn.ypbin.starter.i18n.core.ParamHeaderLocaleResolver;
import java.util.Locale;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.LocaleResolver;

/**
 * 国际化自动配置。
 *
 * <p>把容器中的 {@link MessageSource} 注入 {@code I18nUtil} 供静态调用；在 Web 环境注册
 * 基于参数/请求头的 {@link ParamHeaderLocaleResolver}。{@code MessageSource} 本身由 Spring Boot
 * 的 MessageSourceAutoConfiguration 依 {@code spring.messages.basename} 装配，此处不重复定义。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ypbin.i18n", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(I18nProperties.class)
public class I18nAutoConfiguration {

    /**
     * 将 MessageSource 注入 I18nUtil 静态持有（用 initializer bean 承载，保证在容器就绪时执行）。
     */
    @Bean
    public I18nUtilInitializer i18nUtilInitializer(MessageSource messageSource) {
        return new I18nUtilInitializer(messageSource);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean(LocaleResolver.class)
    public LocaleResolver localeResolver(I18nProperties properties) {
        Locale defaultLocale = StringUtils.hasText(properties.getDefaultLocale())
            ? Locale.forLanguageTag(properties.getDefaultLocale().replace('_', '-'))
            : Locale.getDefault();
        return new ParamHeaderLocaleResolver(properties.getParamName(), properties.getHeaderName(), defaultLocale);
    }
}

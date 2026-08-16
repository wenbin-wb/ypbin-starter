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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 国际化配置项。
 *
 * @author wenbin
 * @since 2026-07-30
 */
@ConfigurationProperties(prefix = I18nProperties.PREFIX)
public class I18nProperties {

    public static final String PREFIX = "ypbin.i18n";

    /** 是否启用国际化，默认开启 */
    private boolean enabled = true;

    /** 从请求参数取语言的参数名（如 ?lang=en_US） */
    private String paramName = "lang";

    /** 从请求头取语言的头名 */
    private String headerName = "Accept-Language";

    /** 默认语言标签（如 zh_CN / en_US），为空则用系统默认 */
    private String defaultLocale = "zh_CN";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getParamName() {
        return paramName;
    }

    public void setParamName(String paramName) {
        this.paramName = paramName;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public void setDefaultLocale(String defaultLocale) {
        this.defaultLocale = defaultLocale;
    }
}

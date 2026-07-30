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

import org.springframework.context.MessageSource;

/**
 * I18nUtil 初始化器。
 *
 * <p>把容器中的 {@link MessageSource} 注入 {@link I18nUtil} 的静态持有（其 setter 为包级私有，
 * 由本类在同包内桥接），使 {@code I18nUtil.message(...)} 可用。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class I18nUtilInitializer {

    public I18nUtilInitializer(MessageSource messageSource) {
        I18nUtil.setMessageSource(messageSource);
    }
}

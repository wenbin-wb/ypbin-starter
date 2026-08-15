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
package cn.ypbin.starter.ai.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 全局配置项。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@ConfigurationProperties(prefix = AiProperties.PREFIX)
public class AiProperties {

    public static final String PREFIX = "ypbin.ai";

    /** 是否启用 AI 模块，默认开启 */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}

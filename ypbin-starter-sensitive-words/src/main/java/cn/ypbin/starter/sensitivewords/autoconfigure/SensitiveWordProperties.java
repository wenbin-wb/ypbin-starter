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
package cn.ypbin.starter.sensitivewords.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 敏感词配置项。
 *
 * @author wenbin
 * @since 2026-07-30
 */
@ConfigurationProperties(prefix = SensitiveWordProperties.PREFIX)
public class SensitiveWordProperties {

    public static final String PREFIX = "ypbin.sensitive-words";

    /** 是否启用敏感词过滤，默认开启 */
    private boolean enabled = true;

    /** 静态敏感词库（当未提供 SensitiveWordProvider 时使用） */
    private List<String> words = new ArrayList<>();

    /** 替换字符 */
    private char replacement = '*';

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getWords() {
        return words;
    }

    public void setWords(List<String> words) {
        this.words = words;
    }

    public char getReplacement() {
        return replacement;
    }

    public void setReplacement(char replacement) {
        this.replacement = replacement;
    }
}

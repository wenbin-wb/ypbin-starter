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

import cn.hutool.dfa.WordTree;
import cn.ypbin.starter.sensitivewords.aspect.SensitiveWordFilterAspect;
import cn.ypbin.starter.sensitivewords.core.SensitiveWordProvider;
import cn.ypbin.starter.sensitivewords.core.SensitiveWordService;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 敏感词自动配置。
 *
 * <p>装配 {@link SensitiveWordService}。词库来源优先用业务方提供的 {@link SensitiveWordProvider}，
 * 否则用配置项 {@code ypbin.sensitive-words.words} 的静态词库。仅在 Hutool DFA 存在且
 * {@code ypbin.sensitive-words.enabled=true}（默认开启）时生效。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnClass(WordTree.class)
@ConditionalOnProperty(prefix = "ypbin.sensitive-words", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SensitiveWordProperties.class)
public class SensitiveWordAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public SensitiveWordService sensitiveWordService(SensitiveWordProperties properties,
        ObjectProvider<SensitiveWordProvider> providerObjectProvider) {
        SensitiveWordProvider provider = providerObjectProvider.getIfAvailable();
        Collection<String> words = provider != null ? provider.getWords() : properties.getWords();
        // 词库为空时 @SensitiveWordFilter 等同于无操作：本模块默认开启，使用者只加依赖不配词库
        // 就会「以为有过滤」，故启动期显式 WARN（禁静默不生效）
        if (provider == null && (words == null || words.isEmpty())) {
            log.warn("[ypbin-starter] 未提供 SensitiveWordProvider 且 {}.words 为空，敏感词过滤不会命中任何词"
                + "（@SensitiveWordFilter 等同无操作）；请配置词库或提供 SensitiveWordProvider Bean。",
                SensitiveWordProperties.PREFIX);
        }
        return new SensitiveWordService(words);
    }

    @Bean
    @ConditionalOnMissingBean
    public SensitiveWordFilterAspect sensitiveWordFilterAspect(SensitiveWordService sensitiveWordService,
        SensitiveWordProperties properties) {
        return new SensitiveWordFilterAspect(sensitiveWordService, properties);
    }
}

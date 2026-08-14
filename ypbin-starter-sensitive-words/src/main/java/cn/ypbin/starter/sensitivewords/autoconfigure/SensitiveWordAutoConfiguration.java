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

    @Bean
    @ConditionalOnMissingBean
    public SensitiveWordService sensitiveWordService(SensitiveWordProperties properties,
        ObjectProvider<SensitiveWordProvider> providerObjectProvider) {
        SensitiveWordProvider provider = providerObjectProvider.getIfAvailable();
        return new SensitiveWordService(provider != null ? provider.getWords() : properties.getWords());
    }

    @Bean
    @ConditionalOnMissingBean
    public SensitiveWordFilterAspect sensitiveWordFilterAspect(SensitiveWordService sensitiveWordService,
        SensitiveWordProperties properties) {
        return new SensitiveWordFilterAspect(sensitiveWordService, properties);
    }
}

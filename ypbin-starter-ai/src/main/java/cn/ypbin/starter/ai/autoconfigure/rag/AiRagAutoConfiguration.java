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
package cn.ypbin.starter.ai.autoconfigure.rag;

import cn.ypbin.starter.ai.rag.AiRagService;
import cn.ypbin.starter.ai.rag.DefaultAiRagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * AI RAG 自动配置。
 *
 * <p>仅当 classpath 存在 {@link VectorStore}（已引入向量库 starter）且
 * {@code ypbin.ai.rag.enabled=true} 时才装配。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@AutoConfiguration
@ConditionalOnClass(VectorStore.class)
@ConditionalOnProperty(prefix = AiRagProperties.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AiRagProperties.class)
public class AiRagAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiRagAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(AiRagService.class)
    @ConditionalOnBean(VectorStore.class)
    public AiRagService aiRagService(VectorStore vectorStore, AiRagProperties props) {
        log.debug("[ypbin-ai] AiRagService configured with VectorStore: {}",
            vectorStore.getClass().getSimpleName());
        return new DefaultAiRagService(vectorStore, props);
    }
}

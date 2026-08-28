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

import cn.ypbin.starter.ai.chat.AiEmbeddingConfigResolver;
import cn.ypbin.starter.ai.rag.LazySimpleVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * AI 向量存储自动配置（懒加载 SimpleVectorStore 内存实现）。
 *
 * <p>当 classpath 存在 {@link VectorStore}、{@code ypbin.ai.rag.enabled=true} 且
 * 业务方提供了 {@link AiEmbeddingConfigResolver} 时，装配 {@link LazySimpleVectorStore}：
 * Bean 启动即创建（不触发模型解析），首次入库/检索时才解析 embedding 模型并构建
 * 向量库，避免启动阶段无租户上下文或未配置模型导致应用无法启动。</p>
 *
 * <p>不依赖 RediSearch / 外部向量数据库，零基础设施即可打通 RAG 链路。</p>
 *
 * @author wenbin
 * @since 2026-08-17
 */
@AutoConfiguration
@AutoConfigureBefore(AiRagAutoConfiguration.class)
@ConditionalOnClass(VectorStore.class)
@ConditionalOnProperty(prefix = AiRagProperties.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AiRagProperties.class)
public class AiVectorStoreAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiVectorStoreAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore simpleVectorStore(AiRagProperties props,
            AiEmbeddingConfigResolver embeddingResolver) {
        VectorStore store = new LazySimpleVectorStore(embeddingResolver, props.getSimpleStorePath(),
            props.getClientTimeout());
        log.debug("[ypbin-ai] LazySimpleVectorStore configured (delegate built on first use)");
        return store;
    }
}

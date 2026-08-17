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
package cn.ypbin.starter.ai.rag;

import cn.ypbin.starter.ai.autoconfigure.rag.AiVectorStoreAutoConfiguration;
import cn.ypbin.starter.ai.chat.AiEmbeddingConfigResolver;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import java.io.File;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

/**
 * 懒加载 {@link SimpleVectorStore} 代理。
 *
 * <p>向量库 Bean 在应用启动时创建，但真正的模型客户端与向量数据在首次访问时
 * （文档入库 / 检索）才构建——避免启动阶段无租户上下文或未配置 embedding 模型
 * 导致应用无法启动。构建结果缓存，后续访问复用同一实例。</p>
 *
 * <p>业务实现由 {@link AiVectorStoreAutoConfiguration} 装配并通过
 * {@link AiEmbeddingConfigResolver} 动态解析 embedding 模型。</p>
 *
 * @author wenbin
 * @since 2026-08-17
 */
public class LazySimpleVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(LazySimpleVectorStore.class);

    private final AiEmbeddingConfigResolver embeddingResolver;
    private final String storePath;
    private volatile VectorStore delegate;

    public LazySimpleVectorStore(AiEmbeddingConfigResolver embeddingResolver, String storePath) {
        this.embeddingResolver = embeddingResolver;
        this.storePath = storePath;
    }

    private VectorStore delegate() {
        if (delegate == null) {
            synchronized (this) {
                if (delegate == null) {
                    delegate = build();
                }
            }
        }
        return delegate;
    }

    private VectorStore build() {
        AiEmbeddingConfigResolver.AiModelInfo info = embeddingResolver.resolve();
        if (info == null || info.baseUrl() == null || info.baseUrl().isBlank()
                || info.apiKey() == null || info.apiKey().isBlank()
                || info.modelName() == null || info.modelName().isBlank()) {
            throw new IllegalStateException(
                "未配置可用的向量化（embedding）模型：请在 AI 配置中新增 EMBEDDING 类型模型并设为默认");
        }
        ClientOptions options = ClientOptions.builder()
            .apiKey(info.apiKey())
            .baseUrl(normalizeBaseUrl(info.baseUrl()))
            .httpClient(SpringAiOpenAiHttpClient.builder().build())
            .build();
        OpenAIClient client = new OpenAIClientImpl(options);
        OpenAiEmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
            .openAiClient(client)
            .metadataMode(MetadataMode.EMBED)
            .options(OpenAiEmbeddingOptions.builder().model(info.modelName()).build())
            .build();
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        if (storePath != null && !storePath.isBlank()) {
            File file = new File(storePath);
            if (file.exists()) {
                store.load(file);
                log.debug("[ypbin-ai] SimpleVectorStore loaded from {}", storePath);
            }
        }
        log.debug("[ypbin-ai] LazySimpleVectorStore built: baseUrl={}, model={}",
            info.baseUrl(), info.modelName());
        return store;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    @Override
    public void add(List<Document> documents) {
        delegate().add(documents);
    }

    @Override
    public void delete(List<String> idList) {
        delegate().delete(idList);
    }

    @Override
    public void delete(Filter.Expression filterExpression) {
        delegate().delete(filterExpression);
    }

    @Override
    public List<Document> similaritySearch(SearchRequest request) {
        return delegate().similaritySearch(request);
    }

    @Override
    public String getName() {
        return delegate().getName();
    }
}

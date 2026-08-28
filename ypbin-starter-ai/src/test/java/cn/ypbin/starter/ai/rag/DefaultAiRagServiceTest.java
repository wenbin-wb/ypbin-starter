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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.ypbin.starter.ai.autoconfigure.rag.AiRagProperties;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

/**
 * {@link DefaultAiRagService} 单元测试。
 *
 * @author wenbin
 * @since 2026-08-28
 */
class DefaultAiRagServiceTest {

    private VectorStore vectorStore;
    private AiRagProperties properties;
    private DefaultAiRagService ragService;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        properties = new AiRagProperties();
        properties.setSimilarityThreshold(0.6);
        properties.setMaxContextLength(2000);
        ragService = new DefaultAiRagService(vectorStore, properties);
    }

    @Test
    void ingest_enrichesDocumentsWithKnowledgeBaseId() {
        Document doc = new Document("doc-1", "Spring AI 文档内容", Map.of("title", "测试"));
        ragService.ingest("kb-100", List.of(doc));

        verify(vectorStore).add(any());
    }

    @Test
    void ingest_emptyDocuments_doesNothing() {
        ragService.ingest("kb-100", Collections.emptyList());
        ragService.ingest("kb-100", null);
    }

    @Test
    void search_returnsFormattedContextString() {
        Document matchDoc = new Document("doc-1", "Spring AI 是一个用于构建 AI 应用的应用框架。", Map.of("score", 0.85));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(matchDoc));

        List<Document> results = ragService.search("kb-100", "什么是 Spring AI？", 3);

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().getText()).contains("Spring AI 是一个用于构建 AI 应用的应用框架。");
    }

    @Test
    void delete_executesSuccessfully() {
        ragService.delete("kb-100");
        verify(vectorStore).delete(any(Filter.Expression.class));
    }

    @Test
    void deleteDocument_executesSuccessfully() {
        ragService.deleteDocument("kb-100", "doc-1");
        verify(vectorStore).delete(any(Filter.Expression.class));
    }
}

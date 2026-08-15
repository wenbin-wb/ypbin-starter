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

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * {@link AiRagService} 默认实现。
 *
 * <p>基于 Spring AI {@link VectorStore} 实现文档入库与语义检索，
 * 通过 metadata {@code knowledgeBaseId} 实现多知识库隔离。
 *
 * @author wenbin
 * @since 2026-08-15
 */
public class DefaultAiRagService implements AiRagService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiRagService.class);
    private static final String KB_ID_KEY = "knowledgeBaseId";
    private static final String DOC_ID_KEY = "documentId";

    private final VectorStore vectorStore;

    public DefaultAiRagService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void ingest(String knowledgeBaseId, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        // 为每个片段注入 knowledgeBaseId，用于检索时按库过滤
        List<Document> enriched = documents.stream()
            .map(doc -> {
                Map<String, Object> meta = new java.util.HashMap<>(doc.getMetadata());
                meta.put(KB_ID_KEY, knowledgeBaseId);
                return new Document(doc.getId(), doc.getText(), meta);
            })
            .toList();
        vectorStore.add(enriched);
        log.debug("[ypbin-ai] ingested {} chunks into kb={}", enriched.size(), knowledgeBaseId);
    }

    @Override
    public List<Document> search(String knowledgeBaseId, String query, int topK) {
        var b = new FilterExpressionBuilder();
        var filter = b.eq(KB_ID_KEY, knowledgeBaseId).build();
        var request = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .filterExpression(filter)
            .build();
        List<Document> results = vectorStore.similaritySearch(request);
        log.debug("[ypbin-ai] search kb={}, query='{}', hits={}", knowledgeBaseId, query, results.size());
        return results;
    }

    @Override
    public void delete(String knowledgeBaseId) {
        var b = new FilterExpressionBuilder();
        var filter = b.eq(KB_ID_KEY, knowledgeBaseId).build();
        vectorStore.delete(filter);
        log.debug("[ypbin-ai] deleted all vectors in kb={}", knowledgeBaseId);
    }

    @Override
    public void deleteDocument(String knowledgeBaseId, String documentId) {
        var b = new FilterExpressionBuilder();
        var filter = b.and(
            b.eq(KB_ID_KEY, knowledgeBaseId),
            b.eq(DOC_ID_KEY, documentId)
        ).build();
        vectorStore.delete(filter);
        log.debug("[ypbin-ai] deleted document vectors: kb={}, doc={}", knowledgeBaseId, documentId);
    }
}

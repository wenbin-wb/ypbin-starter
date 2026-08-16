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

import cn.ypbin.starter.ai.autoconfigure.rag.AiRagProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final double similarityThreshold;
    private final int maxContextLength;

    public DefaultAiRagService(VectorStore vectorStore, AiRagProperties props) {
        this.vectorStore = vectorStore;
        this.similarityThreshold = props.getSimilarityThreshold();
        this.maxContextLength = props.getMaxContextLength();
    }

    @Override
    public void ingest(String knowledgeBaseId, List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        // 为每个片段注入 knowledgeBaseId，用于检索时按库过滤
        List<Document> enriched = documents.stream()
            .map(doc -> {
                Map<String, Object> meta = new HashMap<>(doc.getMetadata());
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
            .similarityThreshold(similarityThreshold)
            .filterExpression(filter)
            .build();
        List<Document> results = vectorStore.similaritySearch(request);
        log.debug("[ypbin-ai] search kb={}, query='{}', hits={}", knowledgeBaseId, query, results.size());
        return limitContextLength(results);
    }

    /**
     * 限制检索片段总字符数（{@code ypbin.ai.rag.max-context-length}），
     * 超出时按顺序截断文本，防止注入上下文时超出模型窗口。
     */
    private List<Document> limitContextLength(List<Document> documents) {
        if (maxContextLength <= 0 || documents.isEmpty()) {
            return documents;
        }
        int budget = maxContextLength;
        List<Document> limited = new ArrayList<>();
        for (Document doc : documents) {
            String text = doc.getText();
            if (text == null || text.isEmpty()) {
                continue;
            }
            if (text.length() > budget) {
                limited.add(new Document(doc.getId(), text.substring(0, budget), doc.getMetadata()));
                break;
            }
            limited.add(doc);
            budget -= text.length();
            if (budget <= 0) {
                break;
            }
        }
        return limited;
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

    @Override
    public List<Document> searchMultiple(List<String> knowledgeBaseIds, String query,
            int topKPerKb, int maxTotal) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return List.of();
        }
        int per = topKPerKb > 0 ? topKPerKb : 5;
        // 各库独立召回，保留原始顺序（RRF 排序用）
        List<List<Document>> perRankings = new ArrayList<>(knowledgeBaseIds.size());
        for (String kbId : knowledgeBaseIds) {
            perRankings.add(search(kbId, query, per));
        }
        // Reciprocal Rank Fusion：跨库文档按 RRF 综合排序
        Map<String, Document> byId = new LinkedHashMap<>();
        Map<String, Double> rrfScore = new HashMap<>();
        for (List<Document> ranking : perRankings) {
            int rank = 1;
            for (Document doc : ranking) {
                byId.putIfAbsent(doc.getId(), doc);
                rrfScore.merge(doc.getId(), 1.0 / (60 + rank), Double::sum);
                rank++;
            }
        }
        List<String> orderedIds = byId.keySet().stream()
            .sorted(Comparator.comparingDouble((String id) -> rrfScore.getOrDefault(id, 0.0)).reversed())
            .toList();
        int total = maxTotal > 0 ? maxTotal : orderedIds.size();
        return orderedIds.subList(0, Math.min(total, orderedIds.size())).stream()
            .map(byId::get)
            .toList();
    }

    @Override
    public List<Document> searchWithRerank(String knowledgeBaseId, String query, int topK) {
        int k = topK > 0 ? topK : 5;
        // 放大召回（取 3 倍候选），再做关键词重叠精排，避免初筛后候选不足
        List<Document> candidates = search(knowledgeBaseId, query, k * 3);
        if (candidates.size() <= 1) {
            return candidates;
        }
        Set<String> queryTokens = tokenize(query);
        return candidates.stream()
            .sorted(Comparator.comparingDouble((Document d) ->
                overlapScore(queryTokens, d.getText())).reversed())
            .limit(k)
            .toList();
    }

    /**
     * 轻量关键词重叠重排分数：查询命中词数 + 命中词占比加权。
     */
    private double overlapScore(Set<String> queryTokens, String text) {
        if (queryTokens.isEmpty() || text == null || text.isBlank()) {
            return 0.0;
        }
        Set<String> docTokens = tokenize(text);
        long hit = queryTokens.stream().filter(docTokens::contains).count();
        if (hit == 0) {
            return 0.0;
        }
        return hit + (double) hit / queryTokens.size();
    }

    /**
     * 将文本规范为小写词元集合（去标点、按非字母数字切分）。
     */
    private static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        String[] tokens = text.split("[\\p{Punct}\\s，。；：！？、（）【】《》“”‘’]+");
        Set<String> set = new HashSet<>();
        for (String t : tokens) {
            if (!t.isBlank()) {
                set.add(t);
            }
        }
        return set;
    }
}
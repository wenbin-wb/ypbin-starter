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
import org.springframework.ai.document.Document;

/**
 * AI RAG（检索增强生成）服务接口。
 *
 * <p>封装向量化存储与语义检索能力，对 admin 业务层隐藏 VectorStore 细节。
 * 业务方只需注入此接口完成知识库文档的入库与检索操作。
 *
 * @author wenbin
 * @since 2026-08-15
 */
public interface AiRagService {

    /**
     * 将文档片段向量化并写入指定知识库的向量存储。
     *
     * <p>每个 {@link Document} 的 metadata 中应包含 {@code knowledgeBaseId} 字段，
     * 用于后续按知识库过滤检索。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param documents       已切片的文档列表
     */
    void ingest(String knowledgeBaseId, List<Document> documents);

    /**
     * 在指定知识库中执行语义相似检索。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param query           查询文本
     * @param topK            返回最相似的前 K 个片段
     * @return 文档片段列表（含 metadata 用于溯源）
     */
    List<Document> search(String knowledgeBaseId, String query, int topK);

    /**
     * 删除指定知识库的所有向量数据。
     *
     * @param knowledgeBaseId 知识库 ID
     */
    void delete(String knowledgeBaseId);

    /**
     * 删除指定知识库中某个文档的所有向量片段。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param documentId      文档 ID（对应 ai_document 表）
     */
    void deleteDocument(String knowledgeBaseId, String documentId);

    /**
     * 多知识库联合检索：在多个知识库中分别检索并按 RRF 合并排序。
     *
     * @param knowledgeBaseIds 知识库 ID 列表
     * @param query            查询文本
     * @param topKPerKb        每个知识库取前 K 条
     * @param maxTotal         合并后最多返回条数
     * @return 合并召回片段（按相关度降序）
     */
    List<Document> searchMultiple(List<String> knowledgeBaseIds, String query,
            int topKPerKb, int maxTotal);

    /**
     * 检索 + 关键词重叠重排：在向量召回基础上按「查询词与片段文本的关键词重叠度」重排，
     * 提升查询词精确命中的片段优先，改进无外部 rerank 模型时的召回精排。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param query           查询文本
     * @param topK            召回条数
     * @return 重排后的片段列表
     */
    List<Document> searchWithRerank(String knowledgeBaseId, String query, int topK);
}
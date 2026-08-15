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
package cn.ypbin.starter.ai.chat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import reactor.core.publisher.Flux;

/**
 * {@link AiChatService} 默认实现。
 *
 * <p>封装 {@link ChatClient} 的流式与非流式调用，对外隐藏 Spring AI 2.0 框架细节。
 * conversationId 通过 {@code advisorParam(ChatMemory.CONVERSATION_ID, ...)} 注入 Memory Advisor。
 * RAG 通过 {@link RetrievalAugmentationAdvisor} 注入，VectorStore 为 null 时降级普通对话。
 *
 * @author wenbin
 * @since 2026-08-15
 */
public class DefaultAiChatService implements AiChatService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiChatService.class);

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final VectorStore vectorStore;
    private final boolean ragEnabled;
    private final long streamTimeoutMs;

    public DefaultAiChatService(ChatClient chatClient, ChatMemory chatMemory, VectorStore vectorStore,
            boolean ragEnabled, long streamTimeoutMs) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
        this.vectorStore = vectorStore;
        this.ragEnabled = ragEnabled;
        this.streamTimeoutMs = streamTimeoutMs;
    }

    /**
     * 流式响应超时保护：配置大于 0 时生效，避免上游长时间不返回导致连接悬挂。
     */
    private Flux<String> withTimeout(Flux<String> flux) {
        if (streamTimeoutMs <= 0) {
            return flux;
        }
        return flux.timeout(Duration.ofMillis(streamTimeoutMs));
    }

    @Override
    public String chat(String conversationId, String userMessage) {
        log.debug("[ypbin-ai] chat: conversationId={}", conversationId);
        return chatClient.prompt()
            .advisors(spec -> spec
                .advisors(buildAdvisors())
                .param(ChatMemory.CONVERSATION_ID, conversationId))
            .user(userMessage)
            .call()
            .content();
    }

    @Override
    public Flux<String> chatStream(String conversationId, String userMessage) {
        log.debug("[ypbin-ai] chatStream: conversationId={}", conversationId);
        return withTimeout(chatClient.prompt()
            .advisors(spec -> spec
                .advisors(buildAdvisors())
                .param(ChatMemory.CONVERSATION_ID, conversationId))
            .user(userMessage)
            .stream()
            .content());
    }

    @Override
    public Flux<String> chatWithKnowledge(String conversationId, String userMessage, String knowledgeBaseId) {
        if (vectorStore == null) {
            log.warn("[ypbin-ai] VectorStore 未配置，RAG 降级为普通对话");
            return chatStream(conversationId, userMessage);
        }
        log.debug("[ypbin-ai] chatWithKnowledge: conversationId={}, kb={}", conversationId, knowledgeBaseId);
        var retriever = VectorStoreDocumentRetriever.builder()
            .vectorStore(vectorStore)
            .filterExpression(() -> new FilterExpressionBuilder()
                .eq("knowledgeBaseId", knowledgeBaseId).build())
            .build();
        var ragAdvisor = RetrievalAugmentationAdvisor.builder()
            .documentRetriever(retriever)
            .build();
        return withTimeout(chatClient.prompt()
            .advisors(spec -> spec
                .advisors(memoryAdvisor(), ragAdvisor)
                .param(ChatMemory.CONVERSATION_ID, conversationId))
            .user(userMessage)
            .stream()
            .content());
    }

    @Override
    public Flux<String> chatWithSystemPrompt(String conversationId, String systemPrompt, String userMessage) {
        log.debug("[ypbin-ai] chatWithSystemPrompt: conversationId={}", conversationId);
        return withTimeout(chatClient.prompt()
            .system(systemPrompt)
            .advisors(spec -> spec
                .advisors(buildAdvisors())
                .param(ChatMemory.CONVERSATION_ID, conversationId))
            .user(userMessage)
            .stream()
            .content());
    }

    @Override
    public void clearMemory(String conversationId) {
        chatMemory.clear(conversationId);
        log.debug("[ypbin-ai] memory cleared: conversationId={}", conversationId);
    }

    /**
     * 组装当前请求的 Advisor 链：记忆必选，全局 RAG 按配置可选。
     */
    private Advisor[] buildAdvisors() {
        List<Advisor> advisors = new ArrayList<>();
        advisors.add(memoryAdvisor());
        RetrievalAugmentationAdvisor rag = globalRagAdvisor();
        if (rag != null) {
            advisors.add(rag);
        }
        return advisors.toArray(new Advisor[0]);
    }

    /**
     * 记忆 Advisor：历史窗口大小由 {@link org.springframework.ai.chat.memory.MessageWindowChatMemory}
     * 的 maxMessages 控制（见 AiMemoryAutoConfiguration / ypbin.ai.memory.window-size）。
     */
    private MessageChatMemoryAdvisor memoryAdvisor() {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    /**
     * 全局 RAG Advisor：当 {@code ypbin.ai.chat.rag-enabled=true} 且已配置向量库时，
     * 普通对话也自动检索全部知识库片段增强回答；未开启时返回 {@code null}（不注入）。
     */
    private RetrievalAugmentationAdvisor globalRagAdvisor() {
        if (!ragEnabled || vectorStore == null) {
            return null;
        }
        var retriever = VectorStoreDocumentRetriever.builder()
            .vectorStore(vectorStore)
            .build();
        return RetrievalAugmentationAdvisor.builder()
            .documentRetriever(retriever)
            .build();
    }

    /**
     * 从 VectorStore 按知识库 ID 检索文档（供 DefaultAiRagService 调用）。
     */
    public List<Document> retrieveDocuments(String query, int topK, String knowledgeBaseId) {
        if (vectorStore == null) {
            return List.of();
        }
        var retriever = VectorStoreDocumentRetriever.builder()
            .vectorStore(vectorStore)
            .topK(topK)
            .filterExpression(() -> new FilterExpressionBuilder()
                .eq("knowledgeBaseId", knowledgeBaseId).build())
            .build();
        return retriever.retrieve(new Query(query));
    }
}

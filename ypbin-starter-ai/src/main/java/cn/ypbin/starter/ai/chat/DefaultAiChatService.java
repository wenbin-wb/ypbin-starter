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

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.OpenAIClientAsyncImpl;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
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
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
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

    /** yml 模型 starter 装配的 ChatClient；为空时按 {@link AiModelConfigResolver} 动态构建 */
    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final VectorStore vectorStore;
    /** 动态模型解析器（业务方实现，从模型配置表读取当前模型） */
    private final AiModelConfigResolver modelResolver;
    /** 默认系统提示词（动态构建 ChatClient 时注入） */
    private final String defaultSystemPrompt;
    private final boolean ragEnabled;
    private final long streamTimeoutMs;

    public DefaultAiChatService(ChatClient chatClient, ChatMemory chatMemory, VectorStore vectorStore,
            AiModelConfigResolver modelResolver, String defaultSystemPrompt, boolean ragEnabled,
            long streamTimeoutMs) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
        this.vectorStore = vectorStore;
        this.modelResolver = modelResolver;
        this.defaultSystemPrompt = defaultSystemPrompt;
        this.ragEnabled = ragEnabled;
        this.streamTimeoutMs = streamTimeoutMs;
    }

    /**
     * 解析当前会话使用的 ChatClient：优先 yml 装配的实例；
     * 否则通过 {@link AiModelConfigResolver} 动态构建 OpenAI 兼容模型。
     */
    private ChatClient resolveClient() {
        if (chatClient != null) {
            return chatClient;
        }
        AiModelConfigResolver.AiModelInfo info = modelResolver == null ? null : modelResolver.resolve();
        if (info == null || info.baseUrl() == null || info.baseUrl().isBlank()) {
            throw new IllegalStateException("未配置可用的模型，请在 AI 配置中新增并设为默认模型");
        }
        // 传输层客户端必须按请求独立创建：Spring AI 流式调用结束后会关闭持有的
        // OpenAI 客户端（连带关闭底层连接池），共享实例会导致后续请求被拒绝
        ClientOptions options = ClientOptions.builder()
            .apiKey(info.apiKey())
            .baseUrl(normalizeBaseUrl(info.baseUrl()))
            .httpClient(SpringAiOpenAiHttpClient.builder().build())
            .build();
        OpenAIClient client = new OpenAIClientImpl(options);
        OpenAIClientAsync asyncClient = new OpenAIClientAsyncImpl(options);
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
            .openAiClient(client)
            .openAiClientAsync(asyncClient)
            .options(OpenAiChatOptions.builder().model(info.modelName()).build())
            .build();
        ChatClient.Builder builder = ChatClient.builder(chatModel);
        if (defaultSystemPrompt != null && !defaultSystemPrompt.isBlank()) {
            builder.defaultSystem(defaultSystemPrompt);
        }
        log.debug("[ypbin-ai] dynamic ChatClient built: baseUrl={}, model={}", info.baseUrl(), info.modelName());
        return builder.build();
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
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
        return resolveClient().prompt()
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
        return withTimeout(resolveClient().prompt()
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
        return withTimeout(resolveClient().prompt()
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
        return withTimeout(resolveClient().prompt()
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

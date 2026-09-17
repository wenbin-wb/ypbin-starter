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

import cn.ypbin.starter.ai.chat.usage.AiUsageInfo;
import cn.ypbin.starter.ai.chat.usage.AiUsageListener;
import cn.ypbin.starter.ai.chat.usage.AiUsageOutcome;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.OpenAIClientAsyncImpl;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.StreamResponseSpec;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

/**
 * {@link AiChatService} 默认实现。
 *
 * <p>封装 {@link ChatClient} 的流式与非流式调用，对外隐藏 Spring AI 2.0 框架细节。
 * 注意：四个对话方法<b>底层都走 {@code ChatClient#stream()}</b>（同步 {@code chat()} 是
 * 流式结果聚合后阻塞返回），故用量只可能来自流式分片的 {@code metadata.usage}。
 * conversationId 通过 {@code advisorParam(ChatMemory.CONVERSATION_ID, ...)} 注入 Memory Advisor。
 * RAG 通过 {@link RetrievalAugmentationAdvisor} 注入，VectorStore 为 null 时降级普通对话。
 *
 * <p><b>用量回调</b>：四个对话方法共用同一条埋点链路（{@link #trackUsage}），每次订阅恰好触发
 * 一次 {@link AiUsageListener}，成功/失败/取消均上报（取消走 {@code doOnCancel}；超时由埋点链路内的
 * {@code Flux.timeout} 产生，按<b>失败</b>计），用量取自上游 {@link ChatResponseMetadata#getUsage()}，
 * 拿不到时以 {@code null} 上报而非 0；回调异常被隔离并记录完整堆栈，不影响对话输出
 * （详见 {@link AiUsageInfo}）。</p>
 *
 * @author wenbin
 * @since 2026-08-15
 */
public class DefaultAiChatService implements AiChatService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiChatService.class);

    /** 未配置（{@code <= 0}）超时时的兜底截止时间：同步路径必须有限等待，避免线程被永久占用 */
    private static final long DEFAULT_STREAM_TIMEOUT_MS = 60_000L;

    /**
     * 同步阻塞等待相对 {@code Flux.timeout} 多留的错误传播余量：
     * 两个截止时间相同时 {@code block} 自己的取消会先到，超时会被误记成「取消」。
     */
    private static final long TIMEOUT_PROPAGATION_GRACE_MS = 1_000L;

    /** yml 模型 starter 装配的 ChatClient；为空时按 {@link AiModelConfigResolver} 动态构建 */
    @Nullable
    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    @Nullable
    private final VectorStore vectorStore;
    /** 动态模型解析器（业务方实现，从模型配置表读取当前模型） */
    @Nullable
    private final AiModelConfigResolver modelResolver;
    /** 默认系统提示词（动态构建 ChatClient 时注入） */
    private final String defaultSystemPrompt;
    private final boolean ragEnabled;
    private final long streamTimeoutMs;
    /** 动态构建 OpenAI 兼容客户端时的传输层超时（连接 + 读写） */
    private final Duration clientTimeout;
    /** @Tool 工具回调提供者（可空：无工具时动态 ChatClient 不注册工具） */
    @Nullable
    private final ToolCallbackProvider toolCallbackProvider;
    private final List<AiUsageListener> usageListeners;

    public DefaultAiChatService(@Nullable ChatClient chatClient, ChatMemory chatMemory,
            @Nullable VectorStore vectorStore,
            @Nullable AiModelConfigResolver modelResolver, String defaultSystemPrompt, boolean ragEnabled,
            long streamTimeoutMs, @Nullable ToolCallbackProvider toolCallbackProvider) {
        this(chatClient, chatMemory, vectorStore, modelResolver, defaultSystemPrompt, ragEnabled, streamTimeoutMs,
                toolCallbackProvider, List.of());
    }

    public DefaultAiChatService(@Nullable ChatClient chatClient, ChatMemory chatMemory,
            @Nullable VectorStore vectorStore,
            @Nullable AiModelConfigResolver modelResolver, String defaultSystemPrompt, boolean ragEnabled,
            long streamTimeoutMs, @Nullable ToolCallbackProvider toolCallbackProvider, List<AiUsageListener> usageListeners) {
        this(chatClient, chatMemory, vectorStore, modelResolver, defaultSystemPrompt, ragEnabled, streamTimeoutMs,
                toolCallbackProvider, usageListeners, Duration.ofMillis(DEFAULT_STREAM_TIMEOUT_MS));
    }

    public DefaultAiChatService(@Nullable ChatClient chatClient, ChatMemory chatMemory,
            @Nullable VectorStore vectorStore,
            @Nullable AiModelConfigResolver modelResolver, String defaultSystemPrompt, boolean ragEnabled,
            long streamTimeoutMs, @Nullable ToolCallbackProvider toolCallbackProvider, List<AiUsageListener> usageListeners,
            Duration clientTimeout) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
        this.vectorStore = vectorStore;
        this.modelResolver = modelResolver;
        this.defaultSystemPrompt = defaultSystemPrompt;
        this.ragEnabled = ragEnabled;
        this.streamTimeoutMs = streamTimeoutMs;
        this.toolCallbackProvider = toolCallbackProvider;
        this.usageListeners = usageListeners != null ? usageListeners : List.of();
        this.clientTimeout = clientTimeout != null ? clientTimeout : Duration.ofMillis(DEFAULT_STREAM_TIMEOUT_MS);
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
        if (info == null || info.baseUrl() == null || info.baseUrl().isBlank()
                || info.apiKey() == null || info.apiKey().isBlank()) {
            throw new IllegalStateException("未配置可用的模型（缺少接口地址或 API Key），请在 AI 配置中新增并设为默认模型");
        }
        // 传输层客户端必须按请求独立创建：Spring AI 流式调用结束后会关闭持有的
        // OpenAI 客户端（连带关闭底层连接池），共享实例会导致后续请求被拒绝
        ClientOptions options = ClientOptions.builder()
            .apiKey(info.apiKey())
            .baseUrl(normalizeBaseUrl(info.baseUrl()))
            .httpClient(SpringAiOpenAiHttpClient.builder()
                .timeout(clientTimeout)
                .build())
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
        if (toolCallbackProvider != null) {
            builder.defaultTools(toolCallbackProvider);
            log.debug("[ypbin-ai] @Tool 已注册到动态 ChatClient");
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
    private <T> Flux<T> withTimeout(Flux<T> flux) {
        if (streamTimeoutMs <= 0) {
            return flux;
        }
        return flux.timeout(Duration.ofMillis(streamTimeoutMs));
    }

    @Override
    public String chat(String conversationId, String userMessage) {
        log.debug("[ypbin-ai] chat: conversationId={}", conversationId);
        // 同步路径必须始终有硬截止，避免上游挂起时无限占用线程：超时由埋点链路内的 Flux.timeout
        // 产生（见 trackUsage 的 alwaysTimeout），block 只多留一段「错误传播余量」。
        // 若 block 与 Flux.timeout 用同一截止时间，先到的会是 block 自己的取消（实测被记成 CANCELLED），
        // 与「超时＝失败」的语义相悖，且回调可能晚于本方法抛出异常。
        StreamResponseSpec responseSpec = resolveClient().prompt()
            .advisors(spec -> spec
                .advisors(buildAdvisors())
                .param(ChatMemory.CONVERSATION_ID, conversationId))
            .user(userMessage)
            .stream();
        List<String> tokens = trackUsage(responseSpec, conversationId, true)
            .collectList()
            .block(Duration.ofMillis(streamTimeoutMs() + TIMEOUT_PROPAGATION_GRACE_MS));
        return tokens == null ? "" : String.join("", tokens);
    }

    /**
     * 对话埋点链路：从上游响应流采集真实用量，并在终局（完成/异常/取消）各上报一次用量事件。
     *
     * <p>语义与顺序（与 Spring AI 2.0.1 的 {@code DefaultStreamResponseSpec#content()} 逐字对齐，
     * 见 {@link #extractText}）：</p>
     * <ol>
     *   <li>先对<b>原始</b> {@link ChatResponse} 分片采集用量——用量常在「无文本的空分片」里回报，
     *       若放到文本抽取之后再采集会全部丢失；</li>
     *   <li>再抽取文本并过滤空串，得到与 {@code content()} 完全一致的输出流，故埋点不改变对外输出；</li>
     *   <li>超时保护置于文本流「之上」（即三个终局回调的下游之前），因此超时以 {@code onError} 收尾，
     *       一律按<b>失败</b>上报（不是取消）。同步路径（{@code alwaysTimeout=true}）始终启用硬截止
     *       （{@code streamTimeoutMs <= 0} 时用 {@value #DEFAULT_STREAM_TIMEOUT_MS} ms 兜底）；
     *       流式路径（{@code false}）尊重 {@code ypbin.ai.chat.stream-timeout-ms} 的「0＝不超时」语义。</li>
     *   <li>完成/异常/取消三个终局信号互斥，由 {@link AtomicBoolean} 保证「每次订阅恰好一次」。</li>
     * </ol>
     *
     * <p>无监听器时直接走原生 {@code content()}，零额外开销、零行为变化。</p>
     *
     * @param responseSpec  已构造好的流式响应规格
     * @param conversationId 会话 ID
     * @param alwaysTimeout 是否无视「0＝不超时」配置强制启用硬截止（同步阻塞路径必须为 true）
     * @return 与原 {@code content()} 等价的文本增量流
     */
    private Flux<String> trackUsage(StreamResponseSpec responseSpec, String conversationId, boolean alwaysTimeout) {
        if (usageListeners.isEmpty()) {
            return withTimeout(responseSpec.content());
        }
        return Flux.defer(() -> {
            long start = System.currentTimeMillis();
            UsageAccumulator accumulator = new UsageAccumulator();
            AtomicBoolean notified = new AtomicBoolean(false);
            Flux<String> text = responseSpec.chatResponse()
                .doOnNext(accumulator::record)
                .map(DefaultAiChatService::extractText)
                .filter(StringUtils::hasLength);
            Flux<String> timed = alwaysTimeout
                ? text.timeout(Duration.ofMillis(streamTimeoutMs()))
                : withTimeout(text);
            return timed
                .doOnComplete(() -> notifyUsage(notified, accumulator, conversationId,
                    elapsed(start), AiUsageOutcome.SUCCESS, null))
                .doOnError(ex -> notifyUsage(notified, accumulator, conversationId,
                    elapsed(start), AiUsageOutcome.FAILURE, ex))
                .doOnCancel(() -> notifyUsage(notified, accumulator, conversationId,
                    elapsed(start), AiUsageOutcome.CANCELLED, null));
        });
    }

    /**
     * 耗时以<b>订阅时刻</b>为基准：避免「方法调用」与「实际订阅」之间的等待
     * （动态模型路径构建 OpenAI 客户端等）被计入上游响应耗时。
     */
    private static long elapsed(long start) {
        return Math.max(0, System.currentTimeMillis() - start);
    }

    /**
     * 与 Spring AI 2.0.1 {@code DefaultStreamResponseSpec#content()} 的文本抽取逐字对齐。
     *
     * <p>框架实现为
     * {@code Optional.ofNullable(getResult()).map(Generation::getOutput).map(AbstractMessage::getText).orElse("")}，
     * 故 {@code getResult()} 与 {@code getOutput()} 任一为空都给空串（随后被 filter 过滤）——
     * 这里必须同样用 {@code Optional} 链，不能直接 {@code getOutput().getText()}（{@code Generation}
     * 构造器不校验输出非空）。</p>
     *
     * @param response 上游响应分片
     * @return 该分片的文本（无文本时为空串）
     */
    private static String extractText(ChatResponse response) {
        return Optional.ofNullable(response.getResult())
            .map(Generation::getOutput)
            .map(AssistantMessage::getText)
            .orElse("");
    }

    /**
     * 向所有监听器上报一次用量事件：异常全部隔离在此方法内（记录完整堆栈），
     * 绝不向 Reactor 链路抛出——否则会篡改对话主流程的完成/异常语义。
     */
    private void notifyUsage(AtomicBoolean notified, UsageAccumulator accumulator, String conversationId,
            long durationMs, AiUsageOutcome outcome, @Nullable Throwable error) {
        if (!notified.compareAndSet(false, true)) {
            return;
        }
        AiUsageInfo info = accumulator.toInfo(conversationId, durationMs, outcome, error);
        for (AiUsageListener listener : usageListeners) {
            try {
                listener.onUsage(info);
            } catch (Exception ex) {
                // 隔离但不静默：完整堆栈必须落日志，便于宿主排查其审计/计费实现
                log.error("[ypbin-ai] AiUsageListener 回调异常（已隔离，不影响对话主流程）: listener={},"
                    + " conversationId={}, outcome={}", listener.getClass().getName(), conversationId, outcome, ex);
            }
        }
    }

    /**
     * 单次订阅的用量采集器。
     *
     * <p>流式中只有部分分片携带用量（OpenAI 协议默认只在最后一个分片返回），且 Spring AI 会做
     * 累计，故记录「最后一次出现的正值」即该次调用的真实用量。</p>
     *
     * <p>判别规则（必须以字节码为准，不能凭印象）：Spring AI 2.0.1 的
     * {@code EmptyUsage#getPromptTokens()/getCompletionTokens()} <b>返回 0</b>；
     * 框架自带的「无用量」判据 {@code UsageCalculator.isEmpty(usage)} 判的也是
     * {@code getTotalTokens() == 0}（usage 为 null 或 totalTokens 为 0 即为空）；
     * 而 {@code UsageCalculator#getCumulativeUsage} 累计出的 {@code DefaultUsage}
     * 在无用量时同样是 0。也就是说「上游没回报」与「真实 0 token」在框架层用任何判据都无法区分
     * （包括 {@code instanceof EmptyUsage}——累计后的对象已不是 EmptyUsage）。故本类与框架判据保持一致：
     * <b>只有正值才算真实回报</b>，0（或负数、null）一律上报 {@code null}
     * （宁可标注「未知」，也不伪造 0 参与计费）。</p>
     */
    private static final class UsageAccumulator {

        @Nullable
        private String model;
        @Nullable
        private Long promptTokens;
        @Nullable
        private Long generationTokens;
        @Nullable
        private Long totalTokens;

        /**
         * 记录一个上游分片的用量与模型名（缺失的字段保持原值）。
         *
         * @param response 上游响应分片
         */
        void record(ChatResponse response) {
            // ChatResponse#getMetadata() 在「无结果」的构造路径上确实可能为 null
            // （框架自身在 MessageAggregator 里也做了 null 判断），故必须判空
            ChatResponseMetadata metadata = response.getMetadata();
            if (metadata == null) {
                return;
            }
            String reportedModel = metadata.getModel();
            if (reportedModel != null && !reportedModel.isBlank()) {
                this.model = reportedModel;
            }
            Usage usage = metadata.getUsage();
            if (usage == null) {
                return;
            }
            this.promptTokens = positiveOrKeep(usage.getPromptTokens(), this.promptTokens);
            this.generationTokens = positiveOrKeep(usage.getCompletionTokens(), this.generationTokens);
            this.totalTokens = positiveOrKeep(usage.getTotalTokens(), this.totalTokens);
        }

        /** 只有正值才算「上游真实回报」；0/负数/null 一律保留原值（最终为 null＝未知）。 */
        private static @Nullable Long positiveOrKeep(@Nullable Integer candidate, @Nullable Long current) {
            // 不能写成 `candidate > 0 ? candidate.longValue() : current`：三元表达式两侧同为数值类型时
            // 按「二元数值提升」取 long，会强制拆箱 current(=null) 直接 NPE（本类单测已覆盖该回归）
            if (candidate == null || candidate <= 0) {
                return current;
            }
            return candidate.longValue();
        }

        /**
         * 生成终局用量事件。
         *
         * @param conversationId 会话 ID
         * @param durationMs     耗时（毫秒）
         * @param outcome        终局结果
         * @param error          失败异常（成功/取消为 null）
         * @return 用量事件
         */
        AiUsageInfo toInfo(String conversationId, long durationMs, AiUsageOutcome outcome, @Nullable Throwable error) {
            return AiUsageInfo.of(model, conversationId, promptTokens, generationTokens, totalTokens,
                    durationMs, outcome, summarize(error));
        }

        /**
         * 失败原因摘要：类名 + 消息；消息为空时只留类名（避免写出 {@code XxxException: null}）。
         *
         * @param error 失败异常，成功/取消时为 null
         * @return 摘要；成功/取消时为 null
         */
        @Nullable
        private static String summarize(@Nullable Throwable error) {
            if (error == null) {
                return null;
            }
            String message = error.getMessage();
            return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : error.getClass().getSimpleName() + ": " + message;
        }
    }

    /**
     * 同步阻塞等待上限：取配置值，{@code <= 0}（＝不超时）时回退 {@value #DEFAULT_STREAM_TIMEOUT_MS} ms。
     *
     * @return 有效截止时间（毫秒）
     */
    private long streamTimeoutMs() {
        return streamTimeoutMs > 0 ? streamTimeoutMs : DEFAULT_STREAM_TIMEOUT_MS;
    }

    @Override
    public Flux<String> chatStream(String conversationId, String userMessage) {
        log.debug("[ypbin-ai] chatStream: conversationId={}", conversationId);
        StreamResponseSpec responseSpec = resolveClient().prompt()
            .advisors(spec -> spec
                .advisors(buildAdvisors())
                .param(ChatMemory.CONVERSATION_ID, conversationId))
            .user(userMessage)
            .stream();
        return trackUsage(responseSpec, conversationId, false);
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
        StreamResponseSpec responseSpec = resolveClient().prompt()
            .advisors(spec -> spec
                .advisors(memoryAdvisor(), ragAdvisor)
                .param(ChatMemory.CONVERSATION_ID, conversationId))
            .user(userMessage)
            .stream();
        return trackUsage(responseSpec, conversationId, false);
    }

    @Override
    public Flux<String> chatWithSystemPrompt(String conversationId, String systemPrompt, String userMessage) {
        log.debug("[ypbin-ai] chatWithSystemPrompt: conversationId={}", conversationId);
        StreamResponseSpec responseSpec = resolveClient().prompt()
            .system(systemPrompt)
            .advisors(spec -> spec
                .advisors(buildAdvisors())
                .param(ChatMemory.CONVERSATION_ID, conversationId))
            .user(userMessage)
            .stream();
        return trackUsage(responseSpec, conversationId, false);
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
    @Nullable
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

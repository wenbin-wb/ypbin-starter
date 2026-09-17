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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.ypbin.starter.ai.chat.usage.AiUsageInfo;
import cn.ypbin.starter.ai.chat.usage.AiUsageListener;
import cn.ypbin.starter.ai.chat.usage.AiUsageOutcome;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * {@link DefaultAiChatService} 用量回调触发链路测试。
 *
 * <p>覆盖四类硬性契约：① 四个对话方法在成功/失败/取消三条终局路径上各<b>恰好触发一次</b>回调；
 * ② 上游未回报用量时上报 {@code null} 而非 0；③ 埋点不改变对外文本输出；
 * ④ 监听器抛异常既不影响主流程、也不静默（ERROR + 完整堆栈落日志）。</p>
 *
 * @author wenbin
 * @since 2026-09-17
 */
class DefaultAiChatServiceUsageTest {

    private static final String MODEL_NAME = "test-model";
    private static final long TIMEOUT_MS = 10_000L;

    @Test
    void chat_reportsSuccessUsageOnce_withUpstreamTokenValues() {
        RecordingUsageListener listener = new RecordingUsageListener();
        DefaultAiChatService service = service(
                Flux.just(chunk("你", null, null), chunk("好", null, null), chunk("", 11, 3)), List.of(listener));

        String answer = service.chat("conv-1", "问题");

        // 埋点不得改变对外输出：无文本的用量分片必须被过滤（与 ChatClient#content() 同语义）
        assertThat(answer).isEqualTo("你好");
        assertThat(listener.events()).singleElement().satisfies(event -> {
            assertThat(event.outcome()).isEqualTo(AiUsageOutcome.SUCCESS);
            assertThat(event.model()).isEqualTo(MODEL_NAME);
            assertThat(event.conversationId()).isEqualTo("conv-1");
            assertThat(event.promptTokens()).isEqualTo(11L);
            assertThat(event.generationTokens()).isEqualTo(3L);
            assertThat(event.totalTokens()).isEqualTo(14L);
            assertThat(event.usageReported()).isTrue();
            assertThat(event.errorMessage()).isNull();
        });
    }

    @Test
    void chatStream_reportsSuccessUsageOnce() {
        RecordingUsageListener listener = new RecordingUsageListener();
        DefaultAiChatService service = service(
                Flux.just(chunk("流", null, null), chunk("式", 7, 5)), List.of(listener));

        List<String> tokens = service.chatStream("conv-2", "问题").collectList().block();

        assertThat(tokens).containsExactly("流", "式");
        assertThat(listener.events()).singleElement().satisfies(event -> {
            assertThat(event.outcome()).isEqualTo(AiUsageOutcome.SUCCESS);
            assertThat(event.promptTokens()).isEqualTo(7L);
            assertThat(event.generationTokens()).isEqualTo(5L);
            assertThat(event.totalTokens()).isEqualTo(12L);
        });
    }

    @Test
    void chatWithSystemPrompt_reportsSuccessUsageOnce() {
        RecordingUsageListener listener = new RecordingUsageListener();
        DefaultAiChatService service = service(Flux.just(chunk("答", 4, 2)), List.of(listener));

        List<String> tokens = service.chatWithSystemPrompt("conv-3", "你是助手", "问题").collectList().block();

        assertThat(tokens).containsExactly("答");
        assertThat(listener.events()).singleElement()
                .satisfies(event -> assertThat(event.outcome()).isEqualTo(AiUsageOutcome.SUCCESS));
    }

    @Test
    void chatWithKnowledge_withoutVectorStore_reportsUsageOnce() {
        RecordingUsageListener listener = new RecordingUsageListener();
        DefaultAiChatService service = service(Flux.just(chunk("降级答", 4, 2)), List.of(listener));

        List<String> tokens = service.chatWithKnowledge("conv-4", "问题", "kb-1").collectList().block();

        assertThat(tokens).containsExactly("降级答");
        assertThat(listener.events()).singleElement()
                .satisfies(event -> assertThat(event.outcome()).isEqualTo(AiUsageOutcome.SUCCESS));
    }

    @Test
    void chatWithKnowledge_withVectorStore_reportsUsageOnce() {
        RecordingUsageListener listener = new RecordingUsageListener();
        DefaultAiChatService service = service(Flux.just(chunk("增强答", 9, 1)),
                List.of(listener), new StubVectorStore());

        List<String> tokens = service.chatWithKnowledge("conv-5", "问题", "kb-1").collectList().block();

        assertThat(tokens).containsExactly("增强答");
        assertThat(listener.events()).singleElement().satisfies(event -> {
            assertThat(event.outcome()).isEqualTo(AiUsageOutcome.SUCCESS);
            assertThat(event.promptTokens()).isEqualTo(9L);
        });
    }

    @Test
    void chatStream_withoutUsageMetadata_reportsNullInsteadOfZero() {
        RecordingUsageListener listener = new RecordingUsageListener();
        DefaultAiChatService service = service(Flux.just(chunk("答", null, null)), List.of(listener));

        service.chatStream("conv-6", "问题").collectList().block();

        assertThat(listener.events()).singleElement().satisfies(event -> {
            assertThat(event.usageReported()).isFalse();
            assertThat(event.promptTokens()).isNull();
            assertThat(event.generationTokens()).isNull();
            assertThat(event.totalTokens()).isNull();
        });
    }

    @Test
    void chatStream_withFrameworkEmptyUsage_reportsNullInsteadOfZero() {
        RecordingUsageListener listener = new RecordingUsageListener();
        DefaultAiChatService service = service(Flux.just(chunk("答", new EmptyUsage())), List.of(listener));

        service.chatStream("conv-7", "问题").collectList().block();

        // EmptyUsage 的 getPromptTokens()/getCompletionTokens() 返回 0（字节码实证），
        // 与「真实 0 token」不可区分 ⇒ 必须上报 null，绝不落成 0
        assertThat(listener.events()).singleElement().satisfies(event -> {
            assertThat(event.usageReported()).isFalse();
            assertThat(event.promptTokens()).isNull();
            assertThat(event.generationTokens()).isNull();
            assertThat(event.totalTokens()).isNull();
        });
    }

    @Test
    void chatStream_failure_reportsFailureOnce_withPartialUsage() {
        RecordingUsageListener listener = new RecordingUsageListener();
        DefaultAiChatService service = service(
                Flux.concat(Flux.just(chunk("半截", 10, 2)),
                        Flux.error(new IllegalStateException("上游破产"))),
                List.of(listener));

        assertThatThrownBy(() -> service.chatStream("conv-8", "问题").collectList().block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("上游破产");

        assertThat(listener.events()).singleElement().satisfies(event -> {
            assertThat(event.outcome()).isEqualTo(AiUsageOutcome.FAILURE);
            assertThat(event.errorMessage()).contains("IllegalStateException").contains("上游破产");
            // 能拿到多少报多少：失败前拿到的用量仍如实上报
            assertThat(event.promptTokens()).isEqualTo(10L);
            assertThat(event.generationTokens()).isEqualTo(2L);
        });
    }

    @Test
    void chatStream_cancel_reportsCancelledOnce() {
        RecordingUsageListener listener = new RecordingUsageListener();
        DefaultAiChatService service = service(
                Flux.concat(Flux.just(chunk("首片", null, null)), Flux.never()), List.of(listener));

        Disposable subscription = service.chatStream("conv-9", "问题").subscribe(ignored -> { });
        assertThat(listener.events()).isEmpty();
        subscription.dispose();

        assertThat(listener.events()).singleElement().satisfies(event -> {
            assertThat(event.outcome()).isEqualTo(AiUsageOutcome.CANCELLED);
            assertThat(event.conversationId()).isEqualTo("conv-9");
            assertThat(event.errorMessage()).isNull();
        });
    }

    @Test
    void listenerException_doesNotAffectMainFlow_andIsLoggedWithStacktrace() {
        IllegalStateException failure = new IllegalStateException("审计库连不上");
        AiUsageListener broken = usage -> {
            throw failure;
        };
        RecordingUsageListener healthy = new RecordingUsageListener();
        DefaultAiChatService service = service(Flux.just(chunk("正文", 3, 1)), List.of(broken, healthy));

        Logger serviceLogger = (Logger) LoggerFactory.getLogger(DefaultAiChatService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        serviceLogger.addAppender(appender);
        String answer;
        try {
            answer = service.chat("conv-10", "问题");
        } finally {
            serviceLogger.detachAppender(appender);
        }

        // 主流程不受影响：文本照常返回，后续监听器照常收到事件
        assertThat(answer).isEqualTo("正文");
        assertThat(healthy.events()).singleElement()
                .satisfies(event -> assertThat(event.outcome()).isEqualTo(AiUsageOutcome.SUCCESS));
        // 且不静默：ERROR 日志必须带完整堆栈（throwable 代理非空）
        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getThrowableProxy()).isNotNull();
            assertThat(event.getThrowableProxy().getClassName())
                    .isEqualTo(IllegalStateException.class.getName());
        });
    }

    @Test
    void chatStream_withoutListener_keepsNativeContentSemantics() {
        DefaultAiChatService service = service(
                Flux.just(chunk("", 5, 5), chunk("正文", 5, 5)), List.of());

        List<String> tokens = service.chatStream("conv-11", "问题").collectList().block();

        // 无监听器时走原生 content()：空文本分片同样被过滤
        assertThat(tokens).containsExactly("正文");
    }

    @Test
    void chatStream_timeout_isReportedAsFailure() {
        RecordingUsageListener listener = new RecordingUsageListener();
        DefaultAiChatService service = service(
                Flux.concat(Flux.just(chunk("半截", 5, 1)), Flux.never()), List.of(listener), null, 100L);

        assertThatThrownBy(() -> service.chatStream("conv-12", "问题").collectList().block(Duration.ofSeconds(5)))
                .isInstanceOf(Exception.class);

        assertThat(listener.events()).singleElement().satisfies(event -> {
            assertThat(event.outcome()).isEqualTo(AiUsageOutcome.FAILURE);
            assertThat(event.errorMessage()).contains("Timeout");
            assertThat(event.promptTokens()).isEqualTo(5L);
        });
    }

    @Test
    void chat_timeout_isReportedAsFailure_andEventArrivesBeforeException() {
        RecordingUsageListener listener = new RecordingUsageListener();
        DefaultAiChatService service = service(
                Flux.concat(Flux.just(chunk("半截", 5, 1)), Flux.never()), List.of(listener), null, 100L);

        assertThatThrownBy(() -> service.chat("conv-13", "问题")).isInstanceOf(Exception.class);

        // 关键：同步路径不得因 block 与 Flux.timeout 同截止时间而把超时记成 CANCELLED，
        // 也不得让回调晚于本方法抛出异常（review 实测过这两种退化）
        assertThat(listener.events()).singleElement().satisfies(event -> {
            assertThat(event.outcome()).isEqualTo(AiUsageOutcome.FAILURE);
            assertThat(event.errorMessage()).contains("Timeout");
        });
    }

    // ---------------------------------------------------------------- 测试替身

    private static DefaultAiChatService service(Flux<ChatResponse> stream, List<AiUsageListener> listeners) {
        return service(stream, listeners, null, TIMEOUT_MS);
    }

    private static DefaultAiChatService service(Flux<ChatResponse> stream, List<AiUsageListener> listeners,
            @Nullable VectorStore vectorStore) {
        return service(stream, listeners, vectorStore, TIMEOUT_MS);
    }

    private static DefaultAiChatService service(Flux<ChatResponse> stream, List<AiUsageListener> listeners,
            @Nullable VectorStore vectorStore, long streamTimeoutMs) {
        ChatClient chatClient = ChatClient.builder(new StubChatModel(stream)).build();
        return new DefaultAiChatService(chatClient, new StubChatMemory(), vectorStore, null, "系统提示", false,
                streamTimeoutMs, null, listeners);
    }

    /** 只实现流式路径的 ChatModel 替身，让真实 ChatClient 走完 advisor 链。 */
    private static final class StubChatModel implements ChatModel {

        private final Flux<ChatResponse> stream;

        StubChatModel(Flux<ChatResponse> stream) {
            this.stream = stream;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            throw new UnsupportedOperationException("本用例只覆盖流式路径");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return stream;
        }

        @Override
        public ChatOptions getOptions() {
            return ChatOptions.builder().model(MODEL_NAME).build();
        }
    }

    /** 空记忆替身：本用例不校验记忆内容。 */
    private static final class StubChatMemory implements ChatMemory {

        @Override
        public void add(String conversationId, List<Message> messages) {
            // 无状态替身
        }

        @Override
        public List<Message> get(String conversationId) {
            return List.of();
        }

        @Override
        public void clear(String conversationId) {
            // 无状态替身
        }
    }

    /** 返回单条文档的向量库替身，使 RAG 分支拿到非空上下文。 */
    private static final class StubVectorStore implements VectorStore {

        @Override
        public void add(List<Document> documents) {
            // 本用例只走检索
        }

        @Override
        public void delete(List<String> ids) {
            // 本用例只走检索
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
            // 本用例只走检索
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            return List.of(new Document("检索到的片段"));
        }
    }

    /** 计数型假监听器。 */
    private static final class RecordingUsageListener implements AiUsageListener {

        private final List<AiUsageInfo> events = new CopyOnWriteArrayList<>();

        @Override
        public void onUsage(AiUsageInfo usage) {
            events.add(usage);
        }

        List<AiUsageInfo> events() {
            return List.copyOf(events);
        }
    }

    private static ChatResponse chunk(String text, @Nullable Integer promptTokens, @Nullable Integer completionTokens) {
        ChatResponseMetadata.Builder metadata = ChatResponseMetadata.builder().model(MODEL_NAME);
        if (promptTokens != null || completionTokens != null) {
            metadata.usage(new DefaultUsage(promptTokens, completionTokens));
        }
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), metadata.build());
    }

    private static ChatResponse chunk(String text, Usage usage) {
        ChatResponseMetadata metadata = ChatResponseMetadata.builder().model(MODEL_NAME).usage(usage).build();
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), metadata);
    }
}

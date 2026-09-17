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
package cn.ypbin.starter.ai.chat.usage;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * AI 对话单次调用的用量与结果指标模型。
 *
 * <p><b>Token 语义（重要）</b>：三个 token 字段均为可空 {@link Long}，
 * {@code null} 表示<b>上游未回报用量</b>，落地时请按「未知」处理（如数据库 NULL、看板不计入），
 * **不得当作 0 计费或统计**。之所以必须可空：Spring AI 2.0.1 在「上游未返回 usage」时给出的是
 * {@code 0}（{@code EmptyUsage#getPromptTokens()} 直接返回 0；框架自带的判据
 * {@code UsageCalculator.isEmpty(usage)} 判的也是 {@code getTotalTokens() == 0}；
 * 而 {@code MessageAggregator}/{@code UsageCalculator} 累计后的 usage 同样退化成 0 且已不再是
 * {@code EmptyUsage}），即 0 与「真实 0 token」用任何判据都不可区分。
 * 因此本类与框架判据一致：只有正值才算真实回报，0（或负数）一律上报 {@code null}，
 * 绝不以 0 冒充真实值。</p>
 *
 * <p>能拿到多少报多少：用量通常只在流式的<b>最后一个分片</b>回报（Spring AI 2.0.1 的
 * {@code OpenAiChatModel} 仅在 {@code streamOptions == null} 时才默认请求
 * {@code stream_options.include_usage=true}；宿主若自行配置了
 * {@code spring.ai.openai.chat.options.stream-options.*}，需把 {@code include-usage} 显式设为
 * {@code true}，否则上游不会回传用量——此时此处如实上报 {@code null}），
 * 本类取该次调用中最后一次出现正值的真实用量。</p>
 *
 * <p><b>契约变更（相对 3.4.0-SNAPSHOT 之前）</b>：{@code promptTokens/generationTokens/totalTokens}
 * 由 {@code long} 改为可空 {@code Long}，并新增 {@code outcome} 与 {@code errorMessage}
 * （此前无法区分成功/失败/取消，失败与取消的用量会被静默丢弃）。</p>
 *
 * @param model            模型标识；上游未回报模型名时为 {@link #UNKNOWN_MODEL}
 * @param conversationId   会话 ID（用于会话维度聚合）
 * @param promptTokens     提示词 Token 消耗；上游未回报时为 {@code null}
 * @param generationTokens 生成内容 Token 消耗；上游未回报时为 {@code null}
 * @param totalTokens      总计 Token 消耗；上游未回报时为 {@code null}
 * @param durationMs       本次调用耗时（毫秒，流式从订阅开始计到终局信号）
 * @param outcome          终局结果：成功/失败/取消
 * @param errorMessage     失败原因摘要；成功与取消时为 {@code null}
 * @param timestamp        事件时间戳
 * @author wenbin
 * @since 2026-08-28
 */
public record AiUsageInfo(
        String model,
        String conversationId,
        @Nullable Long promptTokens,
        @Nullable Long generationTokens,
        @Nullable Long totalTokens,
        long durationMs,
        AiUsageOutcome outcome,
        @Nullable String errorMessage,
        LocalDateTime timestamp
) {

    /** 上游未回报模型名时的占位标识（明确标记「未知」，不是伪造的模型名）。 */
    public static final String UNKNOWN_MODEL = "unknown";

    /**
     * 构造一条用量事件（时间戳取当前时刻）。
     *
     * @param model            模型标识，{@code null} 时记为 {@link #UNKNOWN_MODEL}
     * @param conversationId   会话 ID
     * @param promptTokens     提示词 Token；未知传 {@code null}
     * @param generationTokens 生成 Token；未知传 {@code null}
     * @param totalTokens      总 Token；未知传 {@code null}
     * @param durationMs       耗时（毫秒）
     * @param outcome          终局结果
     * @param errorMessage     失败原因摘要（成功/取消传 {@code null}）
     * @return 用量事件
     */
    public static AiUsageInfo of(@Nullable String model, String conversationId, @Nullable Long promptTokens,
                                 @Nullable Long generationTokens, @Nullable Long totalTokens, long durationMs,
                                 AiUsageOutcome outcome, @Nullable String errorMessage) {
        return new AiUsageInfo(model == null || model.isBlank() ? UNKNOWN_MODEL : model, conversationId,
                promptTokens, generationTokens, totalTokens, durationMs, outcome, errorMessage, LocalDateTime.now());
    }

    /**
     * 构造「成功」用量事件。
     *
     * @param model            模型标识
     * @param conversationId   会话 ID
     * @param promptTokens     提示词 Token；未知传 {@code null}
     * @param generationTokens 生成 Token；未知传 {@code null}
     * @param totalTokens      总 Token；未知传 {@code null}
     * @param durationMs       耗时（毫秒）
     * @return 用量事件
     */
    public static AiUsageInfo success(@Nullable String model, String conversationId, @Nullable Long promptTokens,
                                      @Nullable Long generationTokens, @Nullable Long totalTokens, long durationMs) {
        return of(model, conversationId, promptTokens, generationTokens, totalTokens, durationMs,
                AiUsageOutcome.SUCCESS, null);
    }

    /**
     * 构造「失败」用量事件（可能已产生部分用量，故仍带上 token 字段）。
     *
     * @param model            模型标识
     * @param conversationId   会话 ID
     * @param promptTokens     提示词 Token；未知传 {@code null}
     * @param generationTokens 生成 Token；未知传 {@code null}
     * @param totalTokens      总 Token；未知传 {@code null}
     * @param durationMs       耗时（毫秒）
     * @param errorMessage     失败原因摘要
     * @return 用量事件
     */
    public static AiUsageInfo failure(@Nullable String model, String conversationId, @Nullable Long promptTokens,
                                      @Nullable Long generationTokens, @Nullable Long totalTokens, long durationMs,
                                      @Nullable String errorMessage) {
        return of(model, conversationId, promptTokens, generationTokens, totalTokens, durationMs,
                AiUsageOutcome.FAILURE, errorMessage);
    }

    /**
     * 构造「调用方取消」用量事件。
     *
     * @param model            模型标识
     * @param conversationId   会话 ID
     * @param promptTokens     提示词 Token；未知传 {@code null}
     * @param generationTokens 生成 Token；未知传 {@code null}
     * @param totalTokens      总 Token；未知传 {@code null}
     * @param durationMs       耗时（毫秒）
     * @return 用量事件
     */
    public static AiUsageInfo cancelled(@Nullable String model, String conversationId, @Nullable Long promptTokens,
                                        @Nullable Long generationTokens, @Nullable Long totalTokens, long durationMs) {
        return of(model, conversationId, promptTokens, generationTokens, totalTokens, durationMs,
                AiUsageOutcome.CANCELLED, null);
    }

    /**
     * 是否拿到上游真实回报的用量（任一字段非空即视为拿到）。
     *
     * @return {@code true} 表示至少有一个 token 字段来自上游真实回报
     */
    public boolean usageReported() {
        return promptTokens != null || generationTokens != null || totalTokens != null;
    }
}

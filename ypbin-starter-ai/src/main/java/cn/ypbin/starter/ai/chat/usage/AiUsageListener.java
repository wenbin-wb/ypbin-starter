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

/**
 * AI Token 消耗与用量监听器 SPI。
 *
 * <p>业务系统实现本接口并注入 Spring 容器后，AI 对话每次调用结束时将自动触发本回调，
 * 用于审计日志记录、计费扣减或用量监控看板统计。</p>
 *
 * <p><b>触发契约</b>：{@code AiChatService} 的每个对话方法（{@code chat} / {@code chatStream} /
 * {@code chatWithKnowledge} / {@code chatWithSystemPrompt}）在一次订阅中**恰好触发一次**回调，
 * 由 {@link AiUsageInfo#outcome()} 区分三类终局：
 * {@link AiUsageOutcome#SUCCESS 成功}、{@link AiUsageOutcome#FAILURE 失败}（含超时）、
 * {@link AiUsageOutcome#CANCELLED 取消}（SSE 客户端断开、下游 {@code dispose()} 等）。
 * 取消也会触发一次明确事件，因此「有请求但无记录」不再是可能的黑洞。</p>
 *
 * <p><b>Token 可空</b>：上游未回报用量时 token 字段为 {@code null}（详见 {@link AiUsageInfo}），
 * 绝不填 0 冒充；实现方切勿把 {@code null} 折算成 0 计费。</p>
 *
 * <p><b>异常隔离</b>：实现抛出的任何异常都被 starter 捕获并记录完整堆栈
 * （{@code log.error}），<b>不会</b>影响对话主流式输出，也不会改变主流程的成功/失败语义。
 * 因此实现方应自行保证幂等与快速返回，避免阻塞对话线程。</p>
 *
 * @author wenbin
 * @since 2026-08-28
 */
@FunctionalInterface
public interface AiUsageListener {

    /**
     * 接收 AI 对话用量数据。
     *
     * @param usage 用量元数据（token 字段可能为 {@code null} 表示上游未回报）
     */
    void onUsage(AiUsageInfo usage);
}

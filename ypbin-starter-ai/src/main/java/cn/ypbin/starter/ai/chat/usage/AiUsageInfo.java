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

/**
 * AI Token 消耗与时延用量指标模型。
 *
 * @param model 模型标识名称
 * @param conversationId 会话 ID（可空）
 * @param promptTokens 提示词 Token 消耗
 * @param generationTokens 生成内容 Token 消耗
 * @param totalTokens 总计 Token 消耗
 * @param durationMs 响应耗时（毫秒）
 * @param timestamp 记录时间戳
 * @author wenbin
 * @since 2026-08-28
 */
public record AiUsageInfo(
        String model,
        String conversationId,
        long promptTokens,
        long generationTokens,
        long totalTokens,
        long durationMs,
        LocalDateTime timestamp
) {

    public static AiUsageInfo of(String model, String conversationId, long promptTokens,
                                 long generationTokens, long totalTokens, long durationMs) {
        return new AiUsageInfo(model, conversationId, promptTokens, generationTokens, totalTokens, durationMs, LocalDateTime.now());
    }
}

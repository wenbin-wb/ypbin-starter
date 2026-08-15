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

import reactor.core.publisher.Flux;

/**
 * AI 对话服务接口。
 *
 * <p>对 admin 业务层暴露的唯一 AI 对话入口，屏蔽 Spring AI 框架细节。
 * 业务方只需注入此接口，不引入任何 {@code org.springframework.ai.*} 类。
 *
 * <p>所有方法均基于 conversationId 维护对话历史：
 * 同一 conversationId 的多次调用自动携带 Memory 中的历史消息。
 *
 * @author wenbin
 * @since 2026-08-15
 */
public interface AiChatService {

    /**
     * 非流式对话，等待完整回复后返回。
     *
     * @param conversationId 会话 ID，用于隔离不同会话的 Memory
     * @param userMessage    用户消息
     * @return AI 完整回复
     */
    String chat(String conversationId, String userMessage);

    /**
     * 流式对话，返回逐 token 的增量内容流。
     *
     * <p>使用方通过订阅 {@code Flux<String>} 获取每个 token，
     * 结合 SSE/WebSocket 推送给前端实现打字机效果。
     *
     * @param conversationId 会话 ID
     * @param userMessage    用户消息
     * @return token 增量流
     */
    Flux<String> chatStream(String conversationId, String userMessage);

    /**
     * 携带知识库的 RAG 对话（流式）。
     *
     * <p>先从指定知识库检索相关片段，拼入 context 后再调用模型，
     * 回答中包含来源 metadata（文件名、段落位置）。
     *
     * @param conversationId  会话 ID
     * @param userMessage     用户消息
     * @param knowledgeBaseId 知识库 ID（对应 ai_knowledge_base 表）
     * @return token 增量流
     */
    Flux<String> chatWithKnowledge(String conversationId, String userMessage, String knowledgeBaseId);

    /**
     * 使用指定系统提示词模板的流式对话。
     *
     * <p>系统提示词支持占位符，由业务方负责在调用前替换（如 {username}）。
     *
     * @param conversationId 会话 ID
     * @param systemPrompt   系统提示词（已替换占位符）
     * @param userMessage    用户消息
     * @return token 增量流
     */
    Flux<String> chatWithSystemPrompt(String conversationId, String systemPrompt, String userMessage);

    /**
     * 清除指定会话的历史记忆。
     *
     * @param conversationId 会话 ID
     */
    void clearMemory(String conversationId);
}

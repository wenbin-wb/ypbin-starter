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
package cn.ypbin.starter.ai.autoconfigure.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 对话配置项。
 *
 * <p>控制 ChatClient 的默认行为：系统提示词、历史窗口大小、Token 限速等。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@ConfigurationProperties(prefix = AiChatProperties.PREFIX)
public class AiChatProperties {

    public static final String PREFIX = "ypbin.ai.chat";

    /** 是否启用对话能力，默认开启 */
    private boolean enabled = true;

    /** 默认系统提示词。使用 DeepSeek/GPT 等模型时作为 system 角色消息注入 */
    private String defaultSystemPrompt = "你是一个专业的企业级 AI 助手，请用简洁清晰的中文回答问题。";

    /** 是否在对话中启用 RAG 检索增强（需要同时配置 ypbin.ai.rag.enabled=true）*/
    private boolean ragEnabled = false;

    /** 流式响应超时（毫秒），0 表示不超时 */
    private long streamTimeoutMs = 0L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultSystemPrompt() {
        return defaultSystemPrompt;
    }

    public void setDefaultSystemPrompt(String defaultSystemPrompt) {
        this.defaultSystemPrompt = defaultSystemPrompt;
    }

    public boolean isRagEnabled() {
        return ragEnabled;
    }

    public void setRagEnabled(boolean ragEnabled) {
        this.ragEnabled = ragEnabled;
    }

    public long getStreamTimeoutMs() {
        return streamTimeoutMs;
    }

    public void setStreamTimeoutMs(long streamTimeoutMs) {
        this.streamTimeoutMs = streamTimeoutMs;
    }
}

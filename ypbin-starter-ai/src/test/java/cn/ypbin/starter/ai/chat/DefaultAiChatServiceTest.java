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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * {@link DefaultAiChatService} 单元测试。
 *
 * @author wenbin
 * @since 2026-08-28
 */
class DefaultAiChatServiceTest {

    @Test
    void chat_throwsException_whenNoModelConfigured() {
        DefaultAiChatService chatService = new DefaultAiChatService(
                null, null, null, null, "Default System", false, 10000, null);

        assertThatThrownBy(() -> chatService.chat("conv-1", "你好"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未配置可用的模型");
    }

    @Test
    void chatStream_throwsException_whenNoModelConfigured() {
        DefaultAiChatService chatService = new DefaultAiChatService(
                null, null, null, null, "Default System", false, 10000, null);

        assertThatThrownBy(() -> chatService.chatStream("conv-1", "你好"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未配置可用的模型");
    }

    @Test
    void chatWithKnowledge_throwsException_whenNoModelConfigured() {
        DefaultAiChatService chatService = new DefaultAiChatService(
                null, null, null, null, "Default System", false, 10000, null);

        assertThatThrownBy(() -> chatService.chatWithKnowledge("conv-1", "你好", "kb-100"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未配置可用的模型");
    }
}

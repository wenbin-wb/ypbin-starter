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

import cn.ypbin.starter.ai.autoconfigure.memory.AiMemoryAutoConfiguration;
import cn.ypbin.starter.ai.chat.AiChatService;
import cn.ypbin.starter.ai.chat.DefaultAiChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * AI 对话自动配置。
 *
 * <p>条件：classpath 存在 {@link ChatModel} + {@code ypbin.ai.chat.enabled=true}（默认开启）。
 * 宿主只需引入模型 starter（如 spring-ai-starter-model-deepseek）即可自动装配。
 *
 * <p>默认接入 DeepSeek V4 Flash（快速、低成本），宿主可通过以下配置切换：
 * <pre>
 * # DeepSeek V4 Pro（高精度）
 * spring.ai.deepseek.chat.options.model=deepseek-v4-pro
 *
 * # OpenAI GPT-5.6
 * spring.ai.openai.chat.options.model=gpt-5.6
 * </pre>
 *
 * @author wenbin
 * @since 2026-08-15
 */
@AutoConfiguration
@AutoConfigureAfter(AiMemoryAutoConfiguration.class)
@ConditionalOnClass(ChatModel.class)
@ConditionalOnProperty(prefix = AiChatProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AiChatProperties.class)
public class AiChatAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiChatAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(ChatClient.class)
    @ConditionalOnBean(ChatModel.class)
    public ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory, AiChatProperties props) {
        log.debug("[ypbin-ai] chatClient configured, systemPrompt length={}",
            props.getDefaultSystemPrompt().length());
        return ChatClient.builder(chatModel)
            .defaultSystem(props.getDefaultSystemPrompt())
            .build();
    }

    /** 装配 {@link AiChatService}，admin 层注入此接口，不直接依赖 Spring AI。 */
    @Bean
    @ConditionalOnMissingBean(AiChatService.class)
    @ConditionalOnBean({ChatMemory.class, ChatModel.class})
    public AiChatService aiChatService(ChatClient chatClient, ChatMemory chatMemory,
            ObjectProvider<VectorStore> vectorStoreProvider) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore != null) {
            log.debug("[ypbin-ai] VectorStore detected, RAG enabled in AiChatService");
        }
        return new DefaultAiChatService(chatClient, chatMemory, vectorStore);
    }
}

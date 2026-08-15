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
package cn.ypbin.starter.ai.autoconfigure.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * AI 对话记忆自动配置。
 *
 * <p>默认装配 InMemory 实现；当 {@code ypbin.ai.memory.type=jdbc} 且 JDBC 依赖存在时，
 * 由内部嵌套类覆盖为 JDBC 持久化实现，重启后会话历史不丢失。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ypbin.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AiMemoryProperties.class)
@Import(AiMemoryAutoConfiguration.JdbcMemoryConfiguration.class)
public class AiMemoryAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiMemoryAutoConfiguration.class);

    /**
     * 默认 InMemory 记忆：无需任何额外依赖，开箱即用。
     * JDBC 嵌套类提供更高优先级的 Bean 时，本 Bean 退让。
     */
    @Bean
    @ConditionalOnMissingBean(ChatMemory.class)
    public ChatMemory inMemoryChatMemory() {
        log.debug("[ypbin-ai] using InMemory chat memory (重启后丢失，生产请切换 ypbin.ai.memory.type=jdbc)");
        return MessageWindowChatMemory.builder()
            .chatMemoryRepository(new InMemoryChatMemoryRepository())
            .maxMessages(20)
            .build();
    }

    /**
     * JDBC 持久化记忆配置（嵌套类隔离可选依赖，防止缺 jar 时 NoClassDefFoundError）。
     *
     * <p>需要：spring-ai-starter-model-chat-memory-repository-jdbc + MySQL 连接。
     */
    @Configuration
    @ConditionalOnProperty(prefix = "ypbin.ai.memory", name = "type", havingValue = "jdbc")
    static class JdbcMemoryConfiguration {

        @Bean
        @ConditionalOnMissingBean(ChatMemory.class)
        public ChatMemory jdbcChatMemory(
                org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository repository,
                AiMemoryProperties props) {
            log.debug("[ypbin-ai] using JDBC chat memory (持久化，重启后历史保留)");
            return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(20)
                .build();
        }
    }
}

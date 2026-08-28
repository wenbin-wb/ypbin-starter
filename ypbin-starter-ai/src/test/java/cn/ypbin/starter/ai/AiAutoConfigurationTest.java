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
package cn.ypbin.starter.ai;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.ai.autoconfigure.chat.AiChatAutoConfiguration;
import cn.ypbin.starter.ai.autoconfigure.memory.AiMemoryAutoConfiguration;
import cn.ypbin.starter.ai.autoconfigure.rag.AiRagAutoConfiguration;
import cn.ypbin.starter.ai.chat.AiChatService;
import cn.ypbin.starter.ai.rag.AiRagService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * ypbin-starter-ai 自动配置验证测试。
 *
 * @author wenbin
 * @since 2026-08-15
 */
class AiAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            AiMemoryAutoConfiguration.class,
            AiChatAutoConfiguration.class,
            AiRagAutoConfiguration.class
        ));

    @Test
    void aiDisabledWhenPropertyFalse() {
        runner.withPropertyValues("ypbin.ai.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(AiChatService.class));
    }

    @Test
    void chatDisabledWhenPropertyFalse() {
        runner.withPropertyValues("ypbin.ai.chat.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(AiChatService.class));
    }

    @Test
    void ragNotLoadedWithoutVectorStore() {
        // 没有 VectorStore Bean 时，AiRagService 不应被装配
        runner.withPropertyValues("ypbin.ai.rag.enabled=true")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(AiRagService.class));
    }

    @Test
    void inMemoryChatMemoryLoadedByDefault() {
        runner.run(ctx -> {
            // ChatMemory 应在 Memory 配置中默认装配（InMemory）
            assertThat(ctx).hasSingleBean(ChatMemory.class);
            assertThat(ctx).hasSingleBean(AiChatService.class);
        });
    }
}

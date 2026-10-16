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

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.ai.autoconfigure.memory.AiMemoryAutoConfiguration;
import cn.ypbin.starter.ai.chat.AiChatService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import reactor.core.publisher.Flux;

/**
 * {@code AiChatAutoConfiguration} 中 {@code @ConditionalOnBean(ChatModel.class)} /
 * {@code @ConditionalOnBean(ChatMemory.class)} 顺序风险的 <b>运行时</b>回归用例。
 *
 * <p><b>为何必须用真实 {@code AutoConfigurations}：</b>若把 {@code ChatModel} 作为<b>用户配置</b>
 * Bean 塞进上下文，它的 Bean 定义先于自动配置处理就已存在，顺序问题会被<b>结构性掩盖</b>——
 * 这正是本仓 2026-10-15 事故（v3.9.1）注释里写明的盲区。因此这里只把<b>真实提供者</b>的第三方
 * 自动配置（{@link OpenAiChatAutoConfiguration} / {@link DeepSeekChatAutoConfiguration} /
 * {@link OllamaChatAutoConfiguration}）与 {@code AiChatAutoConfiguration} 一起交给 Boot 的
 * {@code AutoConfigurationSorter}，仅把<b>被测类型无关</b>的依赖（如 {@code ChatMemory}）作为用户 Bean。
 * {@link #userDefinedChatModelMasksOrderingProblem()} 专门用于<b>反证</b>该掩盖效应的存在。</p>
 *
 * <p><b>根因：</b>{@code @ConditionalOnBean} 是 {@code REGISTER_BEAN} 相位条件，只能看见「先于本
 * 自动配置处理完」的 Bean 定义；而 {@code cn.ypbin.*} 在 Boot 自动配置排序中排在
 * {@code org.springframework.*} <b>之前</b>（无显式顺序时按类名字典序）⇒ 第三方模型 starter 提供的
 * {@code ChatModel} / {@code ChatMemory} 尚未注册 ⇒ 条件恒假 ⇒ {@code ChatClient}（乃至
 * {@code AiChatService}）整块静默不装配（无任何报错）。</p>
 *
 * <p>这是 starter v3.9.1 修掉的同一根因（本处为同类第 6 例）。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
class AiChatChatModelOrderingRuntimeTest {

    private static ApplicationContextRunner runnerWith(Class<?>... configurations) {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(configurations));
    }

    @Test
    @DisplayName("★ 第三方 OpenAI 模型提供 ChatModel 时，ChatClient 必须装配")
    void chatClientAssembledWhenOpenAiProvidesChatModel() {
        runnerWith(ToolCallingAutoConfiguration.class, OpenAiChatAutoConfiguration.class,
            AiChatAutoConfiguration.class)
            .withPropertyValues("spring.ai.openai.api-key=test-not-a-real-key",
                "spring.ai.openai.base-url=http://127.0.0.1:1")
            .withBean(ChatMemory.class, AiChatChatModelOrderingRuntimeTest::inMemoryChatMemory)
            .run(context -> {
                printConditionReport(context);
                assertThat(context.getBeansOfType(ChatModel.class))
                    .as("第三方 OpenAI 模型自动配置必须真的提供了 ChatModel（否则本用例无意义）")
                    .hasSize(1);
                assertThat(context.getBeansOfType(ChatClient.class))
                    .as("第三方模型提供 ChatModel 时 ChatClient 必须装配；缺失即说明 "
                        + "@ConditionalOnBean(ChatModel.class) 因缺少 @AutoConfigureAfter 恒假"
                        + "（2026-10-15 同类第 6 例，静默失效）")
                    .hasSize(1);
            });
    }

    @Test
    @DisplayName("★ 第三方 DeepSeek 模型提供 ChatModel 时，ChatClient 必须装配")
    void chatClientAssembledWhenDeepSeekProvidesChatModel() {
        runnerWith(ToolCallingAutoConfiguration.class, DeepSeekChatAutoConfiguration.class,
            AiChatAutoConfiguration.class)
            .withPropertyValues("spring.ai.deepseek.api-key=test-not-a-real-key",
                "spring.ai.deepseek.base-url=http://127.0.0.1:1")
            .withBean(ChatMemory.class, AiChatChatModelOrderingRuntimeTest::inMemoryChatMemory)
            .run(context -> {
                printConditionReport(context);
                assertThat(context.getBeansOfType(ChatModel.class))
                    .as("第三方 DeepSeek 模型自动配置必须真的提供了 ChatModel")
                    .hasSize(1);
                assertThat(context.getBeansOfType(ChatClient.class))
                    .as("第三方 DeepSeek 模型提供 ChatModel 时 ChatClient 必须装配")
                    .hasSize(1);
            });
    }

    @Test
    @DisplayName("★ 第三方 Ollama 模型提供 ChatModel 时，ChatClient 必须装配")
    void chatClientAssembledWhenOllamaProvidesChatModel() {
        runnerWith(ToolCallingAutoConfiguration.class, OllamaApiAutoConfiguration.class,
            OllamaChatAutoConfiguration.class, AiChatAutoConfiguration.class)
            .withPropertyValues("spring.ai.ollama.init.pull-model-strategy=never",
                "spring.ai.ollama.base-url=http://127.0.0.1:1")
            .withBean(ChatMemory.class, AiChatChatModelOrderingRuntimeTest::inMemoryChatMemory)
            .run(context -> {
                printConditionReport(context);
                assertThat(context.getBeansOfType(ChatModel.class))
                    .as("第三方 Ollama 模型自动配置必须真的提供了 ChatModel")
                    .hasSize(1);
                assertThat(context.getBeansOfType(ChatClient.class))
                    .as("第三方 Ollama 模型提供 ChatModel 时 ChatClient 必须装配")
                    .hasSize(1);
            });
    }

    @Test
    @DisplayName("★ 真实宿主形态：仓内记忆 + 第三方模型同时在场，ChatClient 与 AiChatService 都必须装配")
    void realHostShapeAssemblesBothChatClientAndAiChatService() {
        runnerWith(ToolCallingAutoConfiguration.class, AiMemoryAutoConfiguration.class,
            OpenAiChatAutoConfiguration.class, AiChatAutoConfiguration.class)
            .withPropertyValues("spring.ai.openai.api-key=test-not-a-real-key",
                "spring.ai.openai.base-url=http://127.0.0.1:1")
            .run(context -> {
                printConditionReport(context);
                assertThat(context.getBeansOfType(ChatModel.class)).hasSize(1);
                assertThat(context.getBeansOfType(ChatClient.class))
                    .as("真实宿主形态下 ChatClient 必须装配（否则 starter 会静默回退到动态模型路径）")
                    .hasSize(1);
                assertThat(context.getBeansOfType(AiChatService.class))
                    .as("真实宿主形态下 AiChatService 必须装配")
                    .hasSize(1);
            });
    }

    @Test
    @DisplayName("★ 第三方 ChatMemory 提供者路径：ChatMemory 由第三方自动配置提供时 AiChatService 必须装配")
    void aiChatServiceAssembledWhenThirdPartyChatMemoryProvidesChatMemory() {
        runnerWith(ToolCallingAutoConfiguration.class, OpenAiChatAutoConfiguration.class,
            ChatMemoryAutoConfiguration.class, AiChatAutoConfiguration.class)
            .withPropertyValues("spring.ai.openai.api-key=test-not-a-real-key",
                "spring.ai.openai.base-url=http://127.0.0.1:1")
            .run(context -> {
                printConditionReport(context);
                assertThat(context.getBeansOfType(ChatMemory.class))
                    .as("第三方 ChatMemory 自动配置必须真的提供了 ChatMemory")
                    .hasSize(1);
                assertThat(context.getBeansOfType(AiChatService.class))
                    .as("ChatMemory 由第三方自动配置提供时 AiChatService 必须装配；缺失即说明 "
                        + "@ConditionalOnBean(ChatMemory.class) 因第三方提供者无顺序而恒假")
                    .hasSize(1);
            });
    }

    @Test
    @DisplayName("★ 无第三方模型时不得误伤：ChatClient 缺席属既有契约（走动态模型路径），AiChatService 仍须装配")
    void inRepoMemoryPathUnchangedWithoutThirdPartyModel() {
        runnerWith(AiMemoryAutoConfiguration.class, AiChatAutoConfiguration.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(ChatMemory.class);
                assertThat(context.getBeansOfType(ChatModel.class))
                    .as("未引入第三方模型 starter 时容器内不应有 ChatModel")
                    .isEmpty();
                assertThat(context.getBeansOfType(ChatClient.class))
                    .as("无 ChatModel 时 ChatClient 缺席＝既有契约（AiChatService 走动态模型解析路径），"
                        + "不得为「保险」而改变")
                    .isEmpty();
                assertThat(context.getBeansOfType(AiChatService.class))
                    .as("仓内 InMemory 记忆路径必须继续装配 AiChatService（既有 @AutoConfigureAfter 不得被误伤）")
                    .hasSize(1);
            });
    }

    /**
     * 结构性掩盖的实证：把 {@code ChatModel} 作为<b>用户配置</b> Bean 塞进上下文时，顺序问题确实会被
     * 掩盖。此用例只用于说明「为何该缺陷长期未被单测发现」，<b>不得</b>作为「无需修复」的依据。
     */
    @Test
    @DisplayName("对照：用户配置直接提供 ChatModel 时条件为真（说明单测为何看不见该缺陷）")
    void userDefinedChatModelMasksOrderingProblem() {
        ChatModel userDefinedModel = new StubChatModel();
        runnerWith(AiChatAutoConfiguration.class)
            .withBean(ChatMemory.class, AiChatChatModelOrderingRuntimeTest::inMemoryChatMemory)
            .withBean(ChatModel.class, () -> userDefinedModel)
            .run(context -> assertThat(context.getBeansOfType(ChatClient.class))
                .as("用户配置 Bean 先于自动配置处理即存在 ⇒ @ConditionalOnBean 为真 ⇒ 顺序缺陷被掩盖")
                .hasSize(1));
    }

    /** 与 {@code AiMemoryAutoConfiguration} 默认实现等价的 InMemory 记忆，仅用于满足被测条件。 */
    private static ChatMemory inMemoryChatMemory() {
        return org.springframework.ai.chat.memory.MessageWindowChatMemory.builder()
            .chatMemoryRepository(new org.springframework.ai.chat.memory.InMemoryChatMemoryRepository())
            .maxMessages(20)
            .build();
    }

    /** 打印条件求值的运行时原文（{@code ConditionEvaluationReport}），供回执取证。 */
    private static void printConditionReport(AssertableApplicationContext context) {
        ConfigurableApplicationContext source =
            context.getSourceApplicationContext(ConfigurableApplicationContext.class);
        ConfigurableListableBeanFactory beanFactory = source.getBeanFactory();
        ConditionEvaluationReport report = ConditionEvaluationReport.get(beanFactory);

        System.out.println("========== [取证] ChatModel / ChatMemory 相关条件判定原文 ==========");
        report.getConditionAndOutcomesBySource().forEach((sourceName, outcomes) -> {
            boolean interesting = sourceName.contains("AiChatAutoConfiguration")
                || sourceName.contains("OpenAiChatAutoConfiguration")
                || sourceName.contains("DeepSeekChatAutoConfiguration")
                || sourceName.contains("OllamaChatAutoConfiguration")
                || sourceName.contains("model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration");
            if (!interesting) {
                return;
            }
            outcomes.forEach(outcome -> System.out.println(
                "[条件报告] source=" + sourceName
                    + " | condition=" + outcome.getCondition().getClass().getSimpleName()
                    + " | match=" + outcome.getOutcome().isMatch()
                    + " | message=" + outcome.getOutcome().getMessage()));
        });
        System.out.println("[装配] ChatModel beans = " + context.getBeansOfType(ChatModel.class).keySet());
        System.out.println("[装配] ChatClient beans = " + context.getBeansOfType(ChatClient.class).keySet());
        System.out.println("[装配] AiChatService beans = " + context.getBeansOfType(AiChatService.class).keySet());
        System.out.println("==================================================================");
    }

    /** 用户配置形态的最小 {@link ChatModel}，仅用于「结构性掩盖」对照。 */
    static final class StubChatModel implements ChatModel {

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(java.util.List.of());
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.empty();
        }
    }
}

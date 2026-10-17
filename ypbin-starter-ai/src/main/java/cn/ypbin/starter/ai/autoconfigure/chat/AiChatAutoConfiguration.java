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
import cn.ypbin.starter.ai.chat.AiModelConfigResolver;
import cn.ypbin.starter.ai.chat.DefaultAiChatService;
import cn.ypbin.starter.ai.chat.usage.AiUsageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallbackProvider;
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
import reactor.core.publisher.Hooks;

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
 * <p><b>为何必须显式声明与第三方提供者的顺序：</b>本类的
 * {@code chatClient} Bean 用 {@code @ConditionalOnBean(ChatModel.class)}、{@code aiChatService} Bean 用
 * {@code @ConditionalOnBean(ChatMemory.class)} 做守卫，而 {@code @ConditionalOnBean} 是
 * {@code REGISTER_BEAN} 相位条件，<b>只看得见「先于本配置类处理完」的 Bean 定义</b>。
 * {@link ChatModel} 与 {@link ChatMemory} 均由<b>第三方（Spring AI）自动配置</b>提供：本模块 pom 以
 * {@code optional} 声明了 OpenAI / DeepSeek / Ollama 模型 starter（其
 * {@code AutoConfiguration.imports} 分别注册 {@code OpenAiChatAutoConfiguration}、
 * {@code DeepSeekChatAutoConfiguration}、{@code OllamaChatAutoConfiguration}，均提供
 * {@code ChatModel}）以及 {@code spring-ai-autoconfigure-model-chat-memory}（
 * {@code ChatMemoryAutoConfiguration}，提供 {@code ChatMemory}）；这些第三方自动配置<b>都没有</b>声明
 * 与本类的顺序。不加顺序约束时，{@code cn.ypbin.starter.ai.*} 在 Boot 自动配置排序中排在
 * {@code org.springframework.*} <b>之前</b>（无显式顺序时按类名字典序）⇒ 条件求值时第三方
 * {@code ChatModel}/{@code ChatMemory} 尚未注册 ⇒ <b>条件恒假</b> ⇒ {@code ChatClient} 乃至
 * {@code AiChatService} 整块静默不装配（无任何报错）⇒ 宿主引入模型 starter 并配好 yml 模型后，
 * starter 却静默回退到 {@code AiModelConfigResolver} 动态构建路径（或直接不可用）。</p>
 *
 * <p>这是 2026-10-15 starter v3.9.1 修掉的「{@code @ConditionalOnBean} 判定自动配置提供的类型导致
 * 条件恒假」同一根因（本处为同类第 6 例）。运行时实证见
 * {@code AiChatChatModelOrderingRuntimeTest}：未加顺序时条件报告为
 * {@code @ConditionalOnBean ... did not find any beans of type ChatModel}，而 {@code ChatModel} Bean
 * 实际存在。</p>
 *
 * <p><b>为何用 {@code name} 而非 {@code Class} 引用第三方提供者：</b>三个模型 starter 均为
 * {@code optional} 依赖，宿主可能只引其一或都不引；{@code name} 形式的 FQCN 由 Boot 的
 * {@code AutoConfigurationSorter} 通过元数据读取，<b>不会类加载</b>，未出现在当前 classpath 的名字被
 * 排序器直接忽略（仅在与本次实际参与的自动配置集合求交时生效），这与本仓 v3.9.1 各处既有处方一致。
 * 仓内的 {@link AiMemoryAutoConfiguration} 仍然用 {@code Class} 引用（编译期可见，顺序仍需保留）。</p>
 *
 * <p><b>各路径的分工：</b>仓内记忆路径靠 {@code value = AiMemoryAutoConfiguration.class} 成立；
 * 第三方模型/记忆路径靠下面 {@code name} 中的第三方提供者成立。两条路径互补，均不得删除。</p>
 *
 * @author wenbin
 * @since 2026-08-15
 */
@AutoConfiguration
@AutoConfigureAfter(value = AiMemoryAutoConfiguration.class, name = {
    "org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration",
    "org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration",
    "org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration",
    "org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration"
})
@ConditionalOnClass(ChatModel.class)
@ConditionalOnProperty(prefix = AiChatProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AiChatProperties.class)
public class AiChatAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiChatAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(ChatClient.class)
    @ConditionalOnBean(ChatModel.class)
    public ChatClient chatClient(ChatModel chatModel, AiChatProperties props) {
        String systemPrompt = props.getDefaultSystemPrompt();
        log.debug("[ypbin-ai] chatClient configured, systemPrompt length={}",
            systemPrompt == null ? 0 : systemPrompt.length());
        ChatClient.Builder builder = ChatClient.builder(chatModel);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            builder.defaultSystem(systemPrompt);
        }
        return builder.build();
    }

    /**
     * 装配 {@link AiChatService}，admin 层注入此接口，不直接依赖 Spring AI。
     *
     * <p>条件放宽为「存在 ChatMemory 且（存在 ChatModel 或业务方提供了动态模型解析器）」：
     * 未在 yml 配置模型 starter 时，通过 {@link AiModelConfigResolver} 动态构建 OpenAI 兼容模型。</p>
     */
    @Bean
    @ConditionalOnMissingBean(AiChatService.class)
    @ConditionalOnBean(ChatMemory.class)
    public AiChatService aiChatService(ObjectProvider<ChatClient> chatClientProvider, ChatMemory chatMemory,
            ObjectProvider<VectorStore> vectorStoreProvider, ObjectProvider<AiModelConfigResolver> modelResolverProvider,
            ObjectProvider<ToolCallbackProvider> toolCallbackProvider,
            ObjectProvider<AiUsageListener> usageListenersProvider,
            AiChatProperties props) {
        // 开启 Reactor 自动 ThreadLocal 传播：配合 ThreadLocalAccessor 在每次 Scheduler
        // 切换时自动快照并还原 ThreadLocal（幂等，重复调用安全）
        Hooks.enableAutomaticContextPropagation();
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (props.isRagEnabled() && vectorStore == null) {
            // 开关已开但没有向量库：RAG 会静默失效（对话不注入检索上下文），故启动期显式 WARN
            log.warn("[ypbin-ai] 已开启 ypbin.ai.chat.rag-enabled=true，但容器中没有 VectorStore，"
                + "RAG 不会生效（对话不会注入检索上下文）；请开启 ypbin.ai.rag.enabled 并提供可用的嵌入模型配置。");
        } else if (vectorStore != null && props.isRagEnabled()) {
            log.debug("[ypbin-ai] VectorStore detected, global RAG enabled");
        }
        return new DefaultAiChatService(chatClientProvider.getIfAvailable(), chatMemory, vectorStore,
            modelResolverProvider.getIfAvailable(), props.getDefaultSystemPrompt(),
            props.isRagEnabled(), props.getStreamTimeoutMs(), toolCallbackProvider.getIfAvailable(),
            usageListenersProvider.orderedStream().toList(), props.getClientTimeout());
    }
}

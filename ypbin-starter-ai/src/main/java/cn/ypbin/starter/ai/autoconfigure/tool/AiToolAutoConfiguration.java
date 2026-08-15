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
package cn.ypbin.starter.ai.autoconfigure.tool;

import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * AI Tool 自动配置。
 *
 * <p>自动扫描容器中所有 Bean，将其中携带 {@code @Tool} 注解方法的 Bean 统一注册为
 * Spring AI Tool，供 ChatClient Advisor 链调用。
 * 业务方只需在任意 Spring Bean 的方法上标注 {@code @Tool(name=..., description=...)} 即可接入。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@AutoConfiguration
@ConditionalOnClass(ToolCallbackProvider.class)
@ConditionalOnProperty(prefix = "ypbin.ai", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiToolAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiToolAutoConfiguration.class);

    /**
     * 收集容器中所有 Bean，通过 {@link MethodToolCallbackProvider} 自动发现
     * 标注 {@code @Tool} 方法并统一暴露给 ChatClient。
     */
    @Bean
    @ConditionalOnMissingBean(ToolCallbackProvider.class)
    public ToolCallbackProvider ypbinToolCallbackProvider(ApplicationContext context) {
        Collection<Object> allBeans = context.getBeansOfType(Object.class).values();
        ToolCallbackProvider provider = MethodToolCallbackProvider.builder()
            .toolObjects(allBeans.toArray())
            .build();
        int toolCount = provider.getToolCallbacks().length;
        if (toolCount > 0) {
            log.debug("[ypbin-ai] discovered {} @Tool methods from container beans", toolCount);
        }
        return provider;
    }
}

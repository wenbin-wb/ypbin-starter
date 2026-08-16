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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.aop.support.AopUtils;
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
     * 收集容器中携带 {@code @Tool} 注解方法的 Bean，通过 {@link MethodToolCallbackProvider}
     * 统一暴露给 ChatClient。仅对含 {@code @Tool} 方法的 Bean 做反射，避免全容器扫描开销。
     */
    @Bean
    @ConditionalOnMissingBean(ToolCallbackProvider.class)
    public ToolCallbackProvider ypbinToolCallbackProvider(ApplicationContext context) {
        Map<String, Object> allBeans = context.getBeansOfType(Object.class);
        List<Object> toolBeans = new ArrayList<>();
        for (Object bean : allBeans.values()) {
            if (containsToolMethod(AopUtils.getTargetClass(bean))) {
                toolBeans.add(bean);
            }
        }
        ToolCallbackProvider provider = MethodToolCallbackProvider.builder()
            .toolObjects(toolBeans.toArray())
            .build();
        log.debug("[ypbin-ai] discovered {} bean(s) with @Tool methods", toolBeans.size());
        return provider;
    }

    /**
     * 判断类、父类与实现的接口上是否存在标注 {@code @Tool} 的方法（接口声明的方法亦计入）。
     */
    private static boolean containsToolMethod(Class<?> clazz) {
        for (Class<?> current = clazz; current != null && current != Object.class;
                current = current.getSuperclass()) {
            if (hasToolMethod(current.getDeclaredMethods())) {
                return true;
            }
        }
        // 接口默认方法也可能声明 @Tool，遍历接口链补充
        for (Class<?> iface : clazz.getInterfaces()) {
            if (hasToolMethod(iface.getDeclaredMethods())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasToolMethod(Method[] methods) {
        for (Method method : methods) {
            if (method.isAnnotationPresent(Tool.class)) {
                return true;
            }
        }
        return false;
    }
}

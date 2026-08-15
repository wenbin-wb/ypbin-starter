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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 对话记忆配置项。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@ConfigurationProperties(prefix = AiMemoryProperties.PREFIX)
public class AiMemoryProperties {

    public static final String PREFIX = "ypbin.ai.memory";

    /**
     * 记忆存储类型。
     * <ul>
     *   <li>{@code in-memory} — 默认，重启后丢失，适合演示</li>
     *   <li>{@code jdbc} — 持久化到 MySQL，需要 spring-ai-starter-model-chat-memory-repository-jdbc</li>
     * </ul>
     */
    private Type type = Type.IN_MEMORY;

    public enum Type {
        IN_MEMORY, JDBC
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }
}

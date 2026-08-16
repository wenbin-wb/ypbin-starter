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

/**
 * 动态模型配置解析器（SPI）。
 *
 * <p>业务方实现此接口，从自身的模型配置表返回当前生效的模型信息；
 * starter 据此动态构建 OpenAI 兼容 {@code ChatModel}，无需在 yml 配置模型 starter。</p>
 *
 * <p>返回 {@code null} 表示未配置可用模型，对话将回退到 Spring AI 自动装配的
 * {@code ChatModel}（yml 模型 starter 路径）。</p>
 *
 * @author wenbin
 * @since 2026-08-16
 */
public interface AiModelConfigResolver {

    /**
     * 解析当前生效的模型配置。
     *
     * @return 模型信息；无可用的模型配置时返回 {@code null}
     */
    AiModelInfo resolve();

    /**
     * OpenAI 兼容模型信息。
     *
     * @param baseUrl   接口基础地址（如 https://api.deepseek.com）
     * @param apiKey    API Key（明文，调用方负责解密）
     * @param modelName 模型名称（如 deepseek-v4-flash）
     */
    record AiModelInfo(String baseUrl, String apiKey, String modelName) {
    }
}

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
 * 动态向量化模型配置解析器（SPI）。
 *
 * <p>业务方实现此接口，从自身的模型配置表返回当前生效的 embedding 模型信息；
 * starter 据此动态构建 OpenAI 兼容 {@code EmbeddingModel} 与向量存储。</p>
 *
 * <p>返回 {@code null} 表示未配置可用的 embedding 模型，RAG 向量化将不启用。</p>
 *
 * @author wenbin
 * @since 2026-08-17
 */
public interface AiEmbeddingConfigResolver {

    /**
     * 解析当前生效的向量化（embedding）模型配置。
     *
     * @return 模型信息；无可用的模型配置时返回 {@code null}
     */
    AiModelInfo resolve();

    /**
     * OpenAI 兼容 embedding 模型信息（复用对话模型的记录结构）。
     *
     * @param baseUrl   接口基础地址（如 https://dashscope.aliyuncs.com）
     * @param apiKey    API Key（明文，调用方负责解密）
     * @param modelName 模型名称（如 text-embedding-v3）
     */
    record AiModelInfo(String baseUrl, String apiKey, String modelName) {
    }
}

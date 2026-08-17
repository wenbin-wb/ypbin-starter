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
package cn.ypbin.starter.ai.autoconfigure.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI RAG（检索增强）配置项。
 *
 * @author wenbin
 * @since 2026-08-15
 */
@ConfigurationProperties(prefix = AiRagProperties.PREFIX)
public class AiRagProperties {

    public static final String PREFIX = "ypbin.ai.rag";

    /** 是否启用 RAG，默认关闭（需要向量库才有意义）*/
    private boolean enabled = false;

    /** 检索最近 TopK 片段，默认 5 */
    private int topK = 5;

    /** 相似度阈值，低于此值的片段不纳入 context，默认 0.7 */
    private double similarityThreshold = 0.7;

    /** 最大 context 长度（字符数），防止超出模型上下文窗口 */
    private int maxContextLength = 8000;

    /** SimpleVectorStore 序列化文件路径；配置后重启不丢向量（自动加载/保存） */
    private String simpleStorePath;

    public String getSimpleStorePath() {
        return simpleStorePath;
    }

    public void setSimpleStorePath(String simpleStorePath) {
        this.simpleStorePath = simpleStorePath;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public int getMaxContextLength() {
        return maxContextLength;
    }

    public void setMaxContextLength(int maxContextLength) {
        this.maxContextLength = maxContextLength;
    }
}

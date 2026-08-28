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
package cn.ypbin.starter.ai.rag;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.ypbin.starter.ai.chat.AiEmbeddingConfigResolver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

/**
 * {@link LazySimpleVectorStore} 单元测试。
 *
 * @author wenbin
 * @since 2026-08-28
 */
class LazySimpleVectorStoreTest {

    @Test
    void delegate_throwsException_whenNoEmbeddingModelConfigured() {
        AiEmbeddingConfigResolver resolver = mock(AiEmbeddingConfigResolver.class);
        when(resolver.resolve()).thenReturn(null);

        LazySimpleVectorStore vectorStore = new LazySimpleVectorStore(resolver, null);

        assertThatThrownBy(() -> vectorStore.add(List.of(new Document("test"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未配置可用的向量化");
    }

    @Test
    void delegate_throwsException_whenResolverReturnsEmptyKeys() {
        AiEmbeddingConfigResolver resolver = mock(AiEmbeddingConfigResolver.class);
        when(resolver.resolve()).thenReturn(new AiEmbeddingConfigResolver.AiModelInfo("", "", ""));

        LazySimpleVectorStore vectorStore = new LazySimpleVectorStore(resolver, null);

        assertThatThrownBy(() -> vectorStore.add(List.of(new Document("test"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未配置可用的向量化");
    }
}

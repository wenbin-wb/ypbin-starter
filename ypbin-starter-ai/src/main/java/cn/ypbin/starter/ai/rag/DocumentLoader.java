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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;

/**
 * 文档加载与切片工具。
 *
 * <p>将文件字节按格式（PDF / Markdown / TXT）解析为 {@link Document} 列表，
 * 并用 TokenTextSplitter 切片为适合向量化的块。
 * 封装在 starter 层，admin 业务代码无需直接引用 spring-ai 的 Reader 类。
 *
 * <p>PDF 解析依赖 {@code spring-ai-pdf-document-reader}（optional）；
 * 未引入该依赖时 PDF 文件退化为纯文本解析。
 *
 * @author wenbin
 * @since 2026-08-15
 */
public final class DocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(DocumentLoader.class);

    private DocumentLoader() {}

    /**
     * 解析文件字节并切片。
     *
     * @param bytes    文件字节
     * @param filename 文件名（用于判断类型和填充 metadata）
     * @param metadata 额外 metadata（如 knowledgeBaseId、documentId）
     * @return 切片后的文档列表
     */
    public static List<Document> loadAndChunk(byte[] bytes, String filename,
            Map<String, Object> metadata) {
        List<Document> rawDocs = parseRaw(bytes, filename);
        List<Document> chunks = new org.springframework.ai.transformer.splitter.TokenTextSplitter()
            .apply(rawDocs);
        // 注入 metadata
        return chunks.stream()
            .map(c -> {
                Map<String, Object> merged = new java.util.HashMap<>(c.getMetadata());
                merged.putAll(metadata);
                return new Document(c.getId(), c.getText(), merged);
            })
            .toList();
    }

    private static List<Document> parseRaw(byte[] bytes, String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return parsePdf(bytes, filename);
        }
        // Markdown / TXT → 直接作为纯文本
        String text = new String(bytes, StandardCharsets.UTF_8);
        return List.of(new Document(text, Map.of("source", filename != null ? filename : "")));
    }

    private static List<Document> parsePdf(byte[] bytes, String filename) {
        try {
            Class.forName("org.springframework.ai.reader.pdf.PagePdfDocumentReader");
            org.springframework.core.io.ByteArrayResource resource =
                new org.springframework.core.io.ByteArrayResource(bytes) {
                    @Override public String getFilename() { return filename; }
                };
            return new org.springframework.ai.reader.pdf.PagePdfDocumentReader(resource).get();
        } catch (ClassNotFoundException e) {
            log.warn("[ypbin-ai] spring-ai-pdf-document-reader 未引入，PDF 文件 {} 退化为纯文本解析", filename);
            String text = new String(bytes, StandardCharsets.UTF_8);
            return List.of(new Document(text, Map.of("source", filename != null ? filename : "")));
        }
    }
}

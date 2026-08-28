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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

/**
 * {@link DocumentLoader} 解析与切片测试。
 *
 * @author wenbin
 * @since 2026-08-15
 */
class DocumentLoaderTest {

    @Test
    void plainTextParsedWithSourceMetadata() {
        byte[] bytes = "hello world".getBytes(StandardCharsets.UTF_8);
        List<Document> chunks = DocumentLoader.loadAndChunk(bytes, "note.txt", Map.of("kb", "1"));
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).getText()).contains("hello world");
        assertThat(chunks.get(0).getMetadata()).containsEntry("kb", "1");
    }

    @Test
    void markdownParsedAsText() {
        byte[] bytes = "# Title\n\nSome **bold** content.".getBytes(StandardCharsets.UTF_8);
        List<Document> chunks = DocumentLoader.loadAndChunk(bytes, "readme.md", Map.of());
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).getText()).contains("Title");
    }

    @Test
    void htmlExtractsMainContent() {
        String html = "<html><head><title>t</title></head><body>"
            + "<nav>nav noise</nav><article><h1>正文</h1><p>核心内容段落</p></article>"
            + "<footer>footer noise</footer></body></html>";
        List<Document> chunks = DocumentLoader.loadAndChunk(
            html.getBytes(StandardCharsets.UTF_8), "page.html", Map.of());
        assertThat(chunks).isNotEmpty();
        String text = chunks.get(0).getText();
        assertThat(text).contains("正文");
        assertThat(text).contains("核心内容段落");
        assertThat(text).doesNotContain("footer noise");
    }

    @Test
    void docxExtractsParagraphsAndTables() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("段落一");
            doc.createParagraph().createRun().setText("段落二");
            var table = doc.createTable(1, 2);
            table.getRow(0).getCell(0).setText("甲");
            table.getRow(0).getCell(1).setText("乙");
            doc.write(out);
        }
        List<Document> chunks = DocumentLoader.loadAndChunk(out.toByteArray(), "doc.docx", Map.of());
        assertThat(chunks).isNotEmpty();
        String text = chunks.get(0).getText();
        assertThat(text).contains("段落一");
        assertThat(text).contains("段落二");
        assertThat(text).contains("甲 | 乙");
    }

    @Test
    void unknownExtensionFallsBackToText() {
        byte[] bytes = "plain data".getBytes(StandardCharsets.UTF_8);
        List<Document> chunks = DocumentLoader.loadAndChunk(bytes, "data.bin", Map.of());
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).getText()).isEqualTo("plain data");
    }

    @Test
    void positiveChunkSizeOverridesDefault() {
        // 大文本 + 小 chunk，验证切片生效（默认 800 token 不会切这段文本）
        String longText = "word ".repeat(2000);
        List<Document> chunks = DocumentLoader.loadAndChunk(
            longText.getBytes(StandardCharsets.UTF_8), "long.txt", Map.of(), 50);
        assertThat(chunks.size()).isGreaterThan(1);
        // 全部切片合并后应还原原文（去掉空白差异），且 metadata 注入到每片
        String joined = chunks.stream().map(Document::getText).reduce("", String::concat);
        assertThat(joined.replace(" ", "")).contains("word".repeat(100));
        assertThat(chunks).allMatch(c -> c.getMetadata().containsKey("source"));
    }
}

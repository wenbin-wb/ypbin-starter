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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.ByteArrayResource;

/**
 * 文档加载与切片工具。
 *
 * <p>将文件字节按格式（PDF / docx / xlsx / html / Markdown / TXT）解析为
 * {@link Document} 列表，并用 TokenTextSplitter 切片为适合向量化的块。
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
        return loadAndChunk(bytes, filename, metadata, null);
    }

    /**
     * 解析文件字节并按可配置 chunk 大小切片。
     *
     * @param bytes     文件字节
     * @param filename  文件名（用于判断类型和填充 metadata）
     * @param metadata  额外 metadata（如 knowledgeBaseId、documentId）
     * @param chunkSize token 块大小（null 用默认值），仅正数生效，保持向后兼容
     * @return 切片后的文档列表
     */
    public static List<Document> loadAndChunk(byte[] bytes, String filename,
            Map<String, Object> metadata, Integer chunkSize) {
        List<Document> rawDocs = parseRaw(bytes, filename);
        var splitterBuilder = TokenTextSplitter.builder();
        // 仅当 chunkSize 为正数时覆盖默认值，保持向后兼容
        if (chunkSize != null && chunkSize > 0) {
            splitterBuilder.withChunkSize(chunkSize);
        }
        List<Document> chunks = splitterBuilder.build().apply(rawDocs);
        // 注入 metadata
        return chunks.stream()
            .map(c -> {
                Map<String, Object> merged = new HashMap<>(c.getMetadata());
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
        if (lower.endsWith(".docx")) {
            return parseDocx(bytes, filename);
        }
        if (lower.endsWith(".xlsx")) {
            return parseXlsx(bytes, filename);
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return parseHtml(bytes, filename);
        }
        // Markdown / TXT → 直接作为纯文本
        String text = new String(bytes, StandardCharsets.UTF_8);
        return List.of(new Document(text, Map.of("source", filename != null ? filename : "")));
    }

    private static List<Document> parsePdf(byte[] bytes, String filename) {
        // spring-ai-pdf-document-reader 为可选依赖：未引入时退化为纯文本解析
        try {
            Class.forName("org.springframework.ai.reader.pdf.PagePdfDocumentReader");
        } catch (ClassNotFoundException e) {
            log.warn("[ypbin-ai] spring-ai-pdf-document-reader 未引入，PDF 文件 {} 退化为纯文本解析", filename);
            String text = new String(bytes, StandardCharsets.UTF_8);
            return List.of(new Document(text, Map.of("source", filename != null ? filename : "")));
        }
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        return new PagePdfDocumentReader(resource).get();
    }

    /**
     * 解析 docx：段落文本 + 表格单元格文本，按顺序拼接。
     */
    private static List<Document> parseDocx(byte[] bytes, String filename) {
        List<String> parts = new ArrayList<>();
        try (InputStream in = new ByteArrayInputStream(bytes);
                XWPFDocument doc = new XWPFDocument(in)) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                String t = p.getText();
                if (t != null && !t.isBlank()) {
                    parts.add(t.trim());
                }
            }
            for (XWPFTable table : doc.getTables()) {
                for (var row : table.getRows()) {
                    List<String> cells = new ArrayList<>();
                    for (var cell : row.getTableCells()) {
                        String t = cell.getText();
                        if (t != null && !t.isBlank()) {
                            cells.add(t.trim());
                        }
                    }
                    if (!cells.isEmpty()) {
                        parts.add(String.join(" | ", cells));
                    }
                }
            }
        } catch (Exception e) {
            log.error("[ypbin-ai] docx 解析失败: filename={}", filename, e);
            throw new IllegalArgumentException("docx 解析失败：" + e.getMessage(), e);
        }
        String text = String.join("\n", parts);
        return List.of(new Document(text, Map.of("source", filename != null ? filename : "")));
    }

    /**
     * 解析 xlsx：每个工作表按行提取单元格文本，行内单元格以表格分隔符拼接。
     */
    private static List<Document> parseXlsx(byte[] bytes, String filename) {
        List<String> parts = new ArrayList<>();
        try (InputStream in = new ByteArrayInputStream(bytes);
                XSSFWorkbook wb = new XSSFWorkbook(in)) {
            for (int s = 0; s < wb.getNumberOfSheets(); s++) {
                XSSFSheet sheet = wb.getSheetAt(s);
                String sheetName = wb.getSheetName(s);
                StringBuilder sb = new StringBuilder();
                sb.append(sheetName).append("\n");
                for (var row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (var cell : row) {
                        String val = switch (cell.getCellType()) {
                            case STRING -> cell.getStringCellValue();
                            case NUMERIC -> numericCellValue(cell);
                            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                            case FORMULA -> cell.getCellFormula();
                            default -> "";
                        };
                        if (val != null && !val.isBlank()) {
                            cells.add(val.trim());
                        }
                    }
                    if (!cells.isEmpty()) {
                        sb.append(String.join(" | ", cells)).append("\n");
                    }
                }
                parts.add(sb.toString().trim());
            }
        } catch (Exception e) {
            log.error("[ypbin-ai] xlsx 解析失败: filename={}", filename, e);
            throw new IllegalArgumentException("xlsx 解析失败：" + e.getMessage(), e);
        }
        String text = String.join("\n\n", parts);
        return List.of(new Document(text, Map.of("source", filename != null ? filename : "")));
    }

    private static String numericCellValue(org.apache.poi.ss.usermodel.Cell cell) {
        double v = cell.getNumericCellValue();
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }

    /**
     * 解析 html：优先提取 article/main/[role=main] 正文，退回 body。
     */
    private static List<Document> parseHtml(byte[] bytes, String filename) {
        String html = new String(bytes, StandardCharsets.UTF_8);
        org.jsoup.nodes.Document doc = Jsoup.parse(html);
        Element main = doc.selectFirst("article,main,[role=main]");
        String text = (main != null ? main : doc.body()).text();
        return List.of(new Document(text, Map.of("source", filename != null ? filename : "")));
    }
}

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
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
 * 封装在 starter 层，admin 业务代码无需直接引用 spring-ai 的 Reader 类。</p>
 *
 * <p><b>可选依赖与降级/失败口径</b>（三者均为 {@code optional}，宿主可能不引入）：</p>
 * <ul>
 *   <li>{@code spring-ai-pdf-document-reader}：未引入时 PDF 退化为纯文本解析（WARN 提示依赖名）；</li>
 *   <li>{@code org.apache.poi:poi-ooxml}：未引入时解析 docx/xlsx 抛 {@link IllegalStateException}
 *       并指明需补的依赖，<b>不</b>静默降级（二进制格式按 UTF-8 解码只会得到乱码）；</li>
 *   <li>{@code org.jsoup:jsoup}：未引入时解析 html 抛 {@link IllegalStateException} 并指明需补的依赖。</li>
 * </ul>
 *
 * <p><b>实现约束（勿破坏）</b>：POI 与 jsoup 的字节码分别隔离在 {@link PoiDocumentParser} 与
 * {@link HtmlDocumentParser}；本类只通过 {@link #isClassPresent(String)} 做存在性探测。
 * 一旦在本类方法体里直接引用 POI/jsoup 类型（尤其是局部变量），类校验会为 StackMapTable 加载它们，
 * 依赖缺席时本类首次调用即 {@code NoClassDefFoundError}，连 txt 都解析不了（2026-10-16 实测）。</p>
 *
 * @author wenbin
 * @since 2026-08-15
 */
public final class DocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(DocumentLoader.class);

    /** 可选依赖探测类：PDF 解析（spring-ai-pdf-document-reader）。 */
    private static final String PDF_READER_CLASS = "org.springframework.ai.reader.pdf.PagePdfDocumentReader";

    /** 可选依赖探测类：docx 解析（org.apache.poi:poi-ooxml）。 */
    private static final String POI_DOCX_CLASS = "org.apache.poi.xwpf.usermodel.XWPFDocument";

    /** 可选依赖探测类：xlsx 解析（org.apache.poi:poi-ooxml）。 */
    private static final String POI_XLSX_CLASS = "org.apache.poi.xssf.usermodel.XSSFWorkbook";

    /** 可选依赖探测类：html 解析（org.jsoup:jsoup）。 */
    private static final String JSOUP_CLASS = "org.jsoup.Jsoup";

    /** 缺失时提示宿主补充的依赖坐标（docx/xlsx）。 */
    private static final String POI_ARTIFACT = "org.apache.poi:poi-ooxml";

    /** 缺失时提示宿主补充的依赖坐标（html）。 */
    private static final String JSOUP_ARTIFACT = "org.jsoup:jsoup";

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
            Map<String, Object> metadata, @Nullable Integer chunkSize) {
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
        if (!isClassPresent(PDF_READER_CLASS)) {
            // 保留 message-only（不打堆栈）：这是「可选依赖是否存在」的探测，ClassNotFoundException 的堆栈
            // 无诊断价值；WARN 文案已给出缺失的依赖名＝启用方式，且降级结果（纯文本解析）对使用者可见
            log.warn("[ypbin-ai] spring-ai-pdf-document-reader 未引入，PDF 文件 {} 退化为纯文本解析"
                + "（如需按 PDF 结构解析请引入 spring-ai-pdf-document-reader）", filename);
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
     *
     * <p>实际解析在 {@link PoiDocumentParser}，本方法只做可选依赖守卫与委托。</p>
     *
     * @param bytes    文件字节
     * @param filename 文件名
     * @return 解析出的文档（未切片）
     */
    private static List<Document> parseDocx(byte[] bytes, String filename) {
        requireClass(POI_DOCX_CLASS, POI_ARTIFACT, "docx");
        return PoiDocumentParser.parseDocx(bytes, filename);
    }

    /**
     * 解析 xlsx：每个工作表按行提取单元格文本。
     *
     * <p>实际解析在 {@link PoiDocumentParser}，本方法只做可选依赖守卫与委托。</p>
     *
     * @param bytes    文件字节
     * @param filename 文件名
     * @return 解析出的文档（未切片）
     */
    private static List<Document> parseXlsx(byte[] bytes, String filename) {
        requireClass(POI_XLSX_CLASS, POI_ARTIFACT, "xlsx");
        return PoiDocumentParser.parseXlsx(bytes, filename);
    }

    /**
     * 解析 html：优先提取 article/main/[role=main] 正文，退回 body。
     *
     * <p>实际解析在 {@link HtmlDocumentParser}，本方法只做可选依赖守卫与委托。</p>
     *
     * @param bytes    文件字节
     * @param filename 文件名
     * @return 解析出的文档（未切片）
     */
    private static List<Document> parseHtml(byte[] bytes, String filename) {
        requireClass(JSOUP_CLASS, JSOUP_ARTIFACT, "html");
        return HtmlDocumentParser.parse(bytes, filename);
    }

    /**
     * 探测类是否存在于本类所在的 classloader。
     *
     * <p>按需探测（{@code initialize=false}），不会触发目标类的静态初始化。</p>
     *
     * @param className 类全限定名
     * @return 存在返回 {@code true}
     */
    static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, DocumentLoader.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            // 「可选依赖缺席」是本探测的合法分支，非吞异常：缺席路径由 requireClass 显式抛错暴露
            return false;
        }
    }

    /**
     * 可选依赖守卫：类缺席时抛可操作的 {@link IllegalStateException}，而非让 JVM 抛
     * {@code NoClassDefFoundError}（后者只说「找不到类」，不告诉使用者要补哪个依赖）。
     *
     * @param className 必需类的全限定名
     * @param artifact  缺失时需补充的 Maven 依赖坐标
     * @param format    文件格式名（用于错误文案）
     */
    private static void requireClass(String className, String artifact, String format) {
        if (!isClassPresent(className)) {
            throw new IllegalStateException("[ypbin-ai] 解析 " + format + " 需要可选依赖 " + artifact
                + "，请显式引入后再解析 " + format + " 文件");
        }
    }
}

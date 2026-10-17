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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;

/**
 * docx / xlsx 解析器（Apache POI）——把 POI 字节码从 {@link DocumentLoader} 隔离出来的实现类。
 *
 * <p><b>为何必须单独成类：</b>{@code poi-ooxml} 在本模块声明为 {@code optional}，宿主可能不引入。
 * 若 POI 类型直接出现在 {@link DocumentLoader} 的方法体局部变量里（如 {@code XWPFDocument doc}），
 * 类校验阶段会为 StackMapTable 加载这些类型；一旦缺席，{@link DocumentLoader} 会在首次调用时抛
 * {@code NoClassDefFoundError}，<b>连 txt/markdown 都解析不了</b>（2026-10-16 剔除 jar 的实证）。
 * 隔离后 {@link DocumentLoader} 自身不引用任何 POI 类型，仅由
 * {@code DocumentLoader#requireClass(String, String, String)} 做类存在性探测，缺席时给出可操作的错误。</p>
 *
 * <p>本类是包内实现细节，不作为对外 API。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
final class PoiDocumentParser {

    private static final Logger log = LoggerFactory.getLogger(PoiDocumentParser.class);

    private PoiDocumentParser() {}

    /**
     * 解析 docx：段落文本 + 表格单元格文本，按顺序拼接。
     *
     * @param bytes    文件字节
     * @param filename 文件名（仅用于 metadata 与错误日志）
     * @return 解析出的文档（未切片）
     */
    static List<Document> parseDocx(byte[] bytes, String filename) {
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
     *
     * @param bytes    文件字节
     * @param filename 文件名（仅用于 metadata 与错误日志）
     * @return 解析出的文档（未切片）
     */
    static List<Document> parseXlsx(byte[] bytes, String filename) {
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

    private static String numericCellValue(Cell cell) {
        double v = cell.getNumericCellValue();
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }
}

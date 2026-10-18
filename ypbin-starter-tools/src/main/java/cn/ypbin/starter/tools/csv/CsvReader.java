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
package cn.ypbin.starter.tools.csv;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * CSV 解析器：RFC 4180 读取侧，与 {@link CsvUtils} 的写侧构成一对。
 *
 * <p><b>为什么要补读侧</b>：{@link CsvUtils} 只负责写出（转义 + BOM + 公式注入中和），
 * 解析侧此前完全缺失。于是每个需要「导入 CSV」的宿主都得自建一套解析器，而 CSV 的引号/跨行/
 * BOM 语义是对外契约的一部分（用户从 Excel 另存的文件必须能被正确解析）——这份逻辑必须只有
 * 一份实现，且可被单测钉死。</p>
 *
 * <p><b>支持的形态（RFC 4180）</b>：</p>
 * <ul>
 *   <li>{@code a,b,c} —— 普通记录；</li>
 *   <li>{@code "a,b",c} —— 引号内的逗号不是分隔符；</li>
 *   <li>{@code "他说""好""",c} —— 引号内两个连续引号表示一个引号字符；</li>
 *   <li>引号字段可跨行（{@code "line1\nline2",c} 是一条记录）；</li>
 *   <li>UTF-8 BOM 自动剥离（Excel「CSV UTF-8」会写 {@code EF BB BF}）；</li>
 *   <li>CRLF / LF / 混合行尾一律接受；</li>
 *   <li>空行（含只有空白字符的行尾空行）不产生记录；记录内的空字段保留为空串。</li>
 * </ul>
 *
 * <p><b>非法形态（显式失败，不静默吞掉）</b>：引号未闭合、引号出现在字段中部（{@code a"b}）、
 * 引号段结束后出现多余字符（{@code "a"b}）。这三类在宽松解析器里常被「按字面量处理」，结果
 * 用户看到「导入成功但值不对」。本类一律记录为逐行错误（{@link CsvRowError}）——调用方可用
 * {@link #parse} 取「成功行 + 逐行错误」，或用 {@link #read} 要求「全对」，后者遇到任何行级错误
 * 直接抛 {@link CsvParseException}。</p>
 *
 * <p><b>列数一致性</b>：CSV 没有内建 schema，本类以<b>首条语法合法的记录</b>（通常是表头）的列数
 * 为基准，其后列数不一致的记录记为 {@link CsvErrorCode#COLUMN_COUNT_MISMATCH} 行级错误，
 * 不进入成功行——列数不齐的记录即便强行返回，业务侧也无法按下标取值。</p>
 *
 * <p><b>上限语义（禁静默截断）</b>：{@link CsvLimits} 的字节数 / 记录数 / 列数任一被突破，
 * 立即抛 {@link CsvLimitExceededException}，<b>绝不</b>返回被截断的部分结果。
 * 记录数上限统计的是<b>扫描到的全部记录</b>（含语法错误行），否则「一万行全错」仍然会耗尽内存。</p>
 *
 * <p><b>空结果约定</b>：任何入口都不返回 {@code null}；空文件返回空 {@link CsvDocument}
 * 或空列表。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
public final class CsvReader {

    /** 单元格分隔符（RFC 4180 只定义逗号）。 */
    private static final char DELIMITER = ',';

    /** 引号字符。 */
    private static final char QUOTE = '"';

    /** UTF-8 BOM 字符（字符串形态）。 */
    private static final char BOM = '\uFEFF';

    private CsvReader() {
    }

    // ------------------------------------------------------------------ 入口

    /**
     * 按默认上限解析 UTF-8 字节（典型用法：上传文件）。
     *
     * @param content 文件字节（可为 {@code null}，视为空文件）
     * @return 解析结果（含成功记录与逐行错误；<b>不为 null</b>）
     * @throws CsvLimitExceededException 字节数/记录数/列数超限
     * @throws CsvParseException         内容不是合法 UTF-8
     */
    public static CsvDocument parse(@Nullable byte[] content) {
        return parse(content, CsvLimits.defaults());
    }

    /**
     * 按指定上限解析 UTF-8 字节。
     *
     * @param content 文件字节（可为 {@code null}，视为空文件）
     * @param limits  解析上限（不可为 {@code null}）
     * @return 解析结果（含成功记录与逐行错误；<b>不为 null</b>）
     * @throws CsvLimitExceededException 字节数/记录数/列数超限
     * @throws CsvParseException         内容不是合法 UTF-8
     */
    public static CsvDocument parse(@Nullable byte[] content, CsvLimits limits) {
        Objects.requireNonNull(limits, "limits");
        if (content == null || content.length == 0) {
            return new CsvDocument(List.of(), List.of());
        }
        if (content.length > limits.maxBytes()) {
            throw new CsvLimitExceededException(CsvErrorCode.FILE_TOO_LARGE,
                "CSV 文件超过上限：实际 " + content.length + " 字节，上限 " + limits.maxBytes() + " 字节");
        }
        return parseText(decodeStrictly(content), limits);
    }

    /**
     * 按默认上限解析文本。
     *
     * @param text CSV 文本（可为 {@code null}，视为空文本）
     * @return 解析结果（含成功记录与逐行错误；<b>不为 null</b>）
     * @throws CsvLimitExceededException 字节数/记录数/列数超限
     */
    public static CsvDocument parseText(@Nullable String text) {
        return parseText(text, CsvLimits.defaults());
    }

    /**
     * 按指定上限解析文本。
     *
     * @param text   CSV 文本（可为 {@code null}，视为空文本）
     * @param limits 解析上限（不可为 {@code null}）
     * @return 解析结果（含成功记录与逐行错误；<b>不为 null</b>）
     * @throws CsvLimitExceededException 字节数/记录数/列数超限
     */
    public static CsvDocument parseText(@Nullable String text, CsvLimits limits) {
        Objects.requireNonNull(limits, "limits");
        if (text == null || text.isEmpty()) {
            return new CsvDocument(List.of(), List.of());
        }
        // 字节上限对文本入口同样生效：按 UTF-8 编码后的真实字节数判断。
        // 否则「先解码再校验」的路径会绕过字节上限（多字节字符下尤其明显）
        int utf8Bytes = text.getBytes(StandardCharsets.UTF_8).length;
        if (utf8Bytes > limits.maxBytes()) {
            throw new CsvLimitExceededException(CsvErrorCode.FILE_TOO_LARGE,
                "CSV 文本超过上限：实际 " + utf8Bytes + " 字节，上限 " + limits.maxBytes() + " 字节");
        }
        String normalized = stripBom(text);
        if (normalized.isEmpty()) {
            return new CsvDocument(List.of(), List.of());
        }
        return new Scanner(normalized, limits).scan();
    }

    /**
     * 严格读取：所有记录都必须语法合法且列数一致，否则抛异常。
     *
     * <p>与 {@link #parse} 的分工：{@code parse} 面向「逐行报错、能导入的先导入」的批量场景；
     * {@code read} 面向「整批要么全对要么全错」的配置/字典类导入，失败信息一次性列全。</p>
     *
     * @param content 文件字节（可为 {@code null}，视为空文件）
     * @return 记录列表（<b>不为 null</b>；顺序即文件顺序）
     * @throws CsvLimitExceededException 字节数/记录数/列数超限
     * @throws CsvParseException         存在行级错误或编码非法（含具体行号与错误码）
     */
    public static List<CsvRow> read(@Nullable byte[] content) {
        return read(content, CsvLimits.defaults());
    }

    /**
     * 严格读取（指定上限）。
     *
     * @param content 文件字节（可为 {@code null}，视为空文件）
     * @param limits  解析上限（不可为 {@code null}）
     * @return 记录列表（<b>不为 null</b>；顺序即文件顺序）
     * @throws CsvLimitExceededException 字节数/记录数/列数超限
     * @throws CsvParseException         存在行级错误或编码非法（含具体行号与错误码）
     */
    public static List<CsvRow> read(@Nullable byte[] content, CsvLimits limits) {
        return requireClean(parse(content, limits));
    }

    /**
     * 严格读取文本。
     *
     * @param text CSV 文本（可为 {@code null}，视为空文本）
     * @return 记录列表（<b>不为 null</b>；顺序即文件顺序）
     * @throws CsvLimitExceededException 字节数/记录数/列数超限
     * @throws CsvParseException         存在行级错误（含具体行号与错误码）
     */
    public static List<CsvRow> readText(@Nullable String text) {
        return requireClean(parseText(text));
    }

    private static List<CsvRow> requireClean(CsvDocument document) {
        if (!document.errors().isEmpty()) {
            CsvRowError first = document.errors().get(0);
            throw new CsvParseException(CsvErrorCode.ROW_ERRORS,
                "CSV 存在 " + document.errors().size() + " 处解析错误，首个错误：" + first.message(),
                document.errors());
        }
        return document.rows();
    }

    // ------------------------------------------------------------------ 解码

    /**
     * 严格解码 UTF-8：非法字节直接失败，而不是静默替换成 U+FFFD。
     *
     * <p>{@code new String(bytes, UTF_8)} 会把非法字节替换成替换字符，于是用户传了 GBK 文件时
     * 看到的是一堆「格式非法」的字段值，真正的病因（编码不对）被掩盖。严格解码让失败可归因。</p>
     */
    private static String decodeStrictly(byte[] content) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException ex) {
            throw new CsvParseException(CsvErrorCode.FILE_NOT_UTF8,
                "CSV 内容不是合法的 UTF-8（请确认文件编码），字节数 " + content.length, List.of(), ex);
        }
    }

    /** 剥离开头的 UTF-8 BOM（只剥一个，且只在文件首部）。 */
    private static String stripBom(String text) {
        return !text.isEmpty() && text.charAt(0) == BOM ? text.substring(1) : text;
    }

    // ------------------------------------------------------------------ 扫描器

    /**
     * 单遍扫描状态机。
     *
     * <p>不用 {@code String#split} / 逐行 {@code BufferedReader}：两者都无法正确处理「引号内的换行」，
     * 而那是 RFC 4180 的必需能力（单元格里放多行备注）。单遍扫描同时保证 O(n) 时间、
     * 且能在越过上限的第一时间失败（而非解析完整个文件后才发现超大）。</p>
     */
    private static final class Scanner {

        private final String text;
        private final CsvLimits limits;
        private final List<CsvRow> rows = new ArrayList<>();
        private final List<CsvRowError> errors = new ArrayList<>();

        /** 当前记录起始物理行号（1 起）；跨行字段期间保持不变。 */
        private int recordLine = 1;

        /** 当前扫描到的物理行号。 */
        private int physicalLine = 1;

        /** 扫描到的记录总数（含语法错误行），用于记录数上限。 */
        private int recordCount;

        /** 首条语法合法记录的列数（{@code -1} = 尚未确定）。 */
        private int baselineColumns = -1;

        Scanner(String text, CsvLimits limits) {
            this.text = text;
            this.limits = limits;
        }

        CsvDocument scan() {
            List<String> cells = new ArrayList<>();
            StringBuilder cell = new StringBuilder();
            boolean inQuotes = false;
            boolean fieldWasQuoted = false;
            boolean recordHasContent = false;
            @Nullable CsvRowError rowError = null;

            int index = 0;
            int length = text.length();
            while (index < length) {
                char ch = text.charAt(index);
                if (inQuotes) {
                    if (ch == QUOTE) {
                        if (index + 1 < length && text.charAt(index + 1) == QUOTE) {
                            cell.append(QUOTE);
                            index += 2;
                            continue;
                        }
                        inQuotes = false;
                        index++;
                        continue;
                    }
                    if (ch == '\n') {
                        // 引号内的换行是字段内容，但物理行号必须递增：
                        // 否则跨行字段之后的记录行号全部偏小，用户按行号找不到出错行
                        physicalLine++;
                    }
                    cell.append(ch);
                    index++;
                    continue;
                }
                if (ch == QUOTE) {
                    if (cell.length() > 0 || fieldWasQuoted) {
                        // 引号只能出现在字段开头：a"b 与 "a"b 都是非法输入
                        rowError = firstError(rowError, CsvErrorCode.MISPLACED_QUOTE,
                            "引号只能出现在字段开头");
                    }
                    inQuotes = true;
                    fieldWasQuoted = true;
                    recordHasContent = true;
                    index++;
                    continue;
                }
                if (ch == DELIMITER) {
                    cells.add(cell.toString());
                    cell.setLength(0);
                    fieldWasQuoted = false;
                    recordHasContent = true;
                    index++;
                    continue;
                }
                if (ch == '\r' || ch == '\n') {
                    // 行尾：CRLF 视为一个换行；裸 CR / 裸 LF 同样接受
                    boolean crlf = ch == '\r' && index + 1 < length && text.charAt(index + 1) == '\n';
                    cells.add(cell.toString());
                    cell.setLength(0);
                    fieldWasQuoted = false;
                    boolean blankRecord = !recordHasContent && isSingleEmptyCell(cells);
                    if (!blankRecord) {
                        finishRecord(cells, rowError);
                    }
                    cells = new ArrayList<>();
                    rowError = null;
                    recordHasContent = false;
                    index += crlf ? 2 : 1;
                    physicalLine++;
                    recordLine = physicalLine;
                    continue;
                }
                if (fieldWasQuoted) {
                    // 引号段结束后只允许分隔符 / 行尾
                    rowError = firstError(rowError, CsvErrorCode.TRAILING_CHAR_AFTER_QUOTE,
                        "引号段结束后出现多余字符");
                }
                cell.append(ch);
                recordHasContent = true;
                index++;
            }

            if (inQuotes) {
                rowError = firstError(rowError, CsvErrorCode.UNCLOSED_QUOTE, "引号未闭合");
            }
            // 文件末尾的最后一条记录（文件不以行尾符结尾时也要收尾）
            cells.add(cell.toString());
            if (recordHasContent || !isSingleEmptyCell(cells)) {
                finishRecord(cells, rowError);
            }
            return new CsvDocument(rows, errors);
        }

        /** 收一条记录：超限立即失败；有行级错误只记错误；列数不一致记错误。 */
        private void finishRecord(List<String> cells, @Nullable CsvRowError rowError) {
            recordCount++;
            if (recordCount > limits.maxRows()) {
                throw new CsvLimitExceededException(CsvErrorCode.TOO_MANY_ROWS,
                    "CSV 记录数超过上限：已扫描 " + recordCount + " 条，上限 " + limits.maxRows() + " 条");
            }
            if (cells.size() > limits.maxColumns()) {
                throw new CsvLimitExceededException(CsvErrorCode.TOO_MANY_COLUMNS,
                    "CSV 第 " + recordLine + " 行超过列数上限：实际 " + cells.size()
                        + " 列，上限 " + limits.maxColumns() + " 列");
            }
            if (rowError != null) {
                errors.add(rowError);
                return;
            }
            if (baselineColumns < 0) {
                baselineColumns = cells.size();
            } else if (cells.size() != baselineColumns) {
                errors.add(new CsvRowError(recordLine, CsvErrorCode.COLUMN_COUNT_MISMATCH,
                    "第 " + recordLine + " 行：列数为 " + cells.size() + "，与首行不一致（首行 "
                        + baselineColumns + " 列）"));
                return;
            }
            rows.add(new CsvRow(recordLine, cells));
        }

        /** 保留首个错误：一行只报一条，且首个错误最接近真实病因。 */
        private CsvRowError firstError(@Nullable CsvRowError existing, CsvErrorCode code, String detail) {
            if (existing != null) {
                return existing;
            }
            return new CsvRowError(recordLine, code, "第 " + recordLine + " 行：" + detail);
        }

        private static boolean isSingleEmptyCell(List<String> cells) {
            return cells.size() == 1 && cells.get(0).isEmpty();
        }
    }
}

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
package cn.ypbin.starter.excel.util;

import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * CSV 导出工具：UTF-8 BOM、RFC 4180 转义与公式注入中和。
 *
 * <p><b>为什么与 Excel 并列放在本模块</b>：{@code ypbin-starter-excel} 的职责是「表格数据的导入导出」，
 * Excel 与 CSV 只是同一职责的两种编码。放在同一模块才能让两者<b>共用</b>下载响应头与文件名编码逻辑
 * （见 {@link DownloadResponses}），避免新增一种格式就复制一份 {@code Content-Disposition} 拼装代码。
 * 本模块不新增任何依赖：CSV 只需要 JDK 与可选的 Servlet API。</p>
 *
 * <p><b>为什么需要这个类</b>：「转义少一层引号」与「危险首字符被 Excel 当公式执行」正是导出功能最容易
 * <b>静默</b>出错的两处：前者只在单元格含逗号/引号/换行时暴露，后者在 <b>Excel/WPS 打开文件的瞬间</b>
 * 才可能触发，服务端与单测都看不到。故这两件事必须是可独立验证的纯函数。</p>
 *
 * <p><b>公式注入（CSV Injection）</b>：以 {@code = + - @ TAB CR} 开头的单元格会被 Excel/WPS 当作公式
 * 解析，攻击者可在设备名称、告警内容等用户可控字段里写入 {@code =cmd|'/C calc'!A1} 之类的载荷，
 * 诱导运维人员打开导出文件时执行。统一前缀单引号中和（表格软件把单引号视为「强制文本」）。
 * <b>负数 {@code -1} 同样在中和范围内</b>：导出不是数值计算入口，安全优先于「负数显示带撇号」的观感。</p>
 *
 * <p><b>安全边界（切勿过度承诺）</b>：中和只处理<b>首字符</b>，这是表格软件解析公式的判定依据。
 * 它不能替代「导出字段的访问控制」——不要把本工具当作可以随意导出敏感列的许可证。</p>
 *
 * <p><b>业务配额不在此处</b>：单次导出行数上限（如设备/告警导出的 10000 条）取决于具体业务对
 * 内存、响应时间与产品策略的取舍，属<b>业务配额</b>而非通用能力。本类<b>有意不提供</b>行数上限常量，
 * 由业务层自行定义并显式拒绝超限请求（禁止静默截断）。</p>
 *
 * <p><b>空值约定</b>：单元格允许为 {@code null}（输出为空串），{@link #formatDateTime(LocalDateTime)}
 * 对 {@code null} 返回空串；{@link #toCsv} 的表头与数据行列表本身不可为 {@code null}。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
public final class CsvUtils {

    /** UTF-8 BOM：Excel 对无 BOM 的 UTF-8 CSV 常按本机编码解读，中文列会乱码。 */
    public static final String UTF8_BOM = "\uFEFF";

    /** CSV 内容类型（带 charset：避免表格软件按本机编码解读）。 */
    public static final String CSV_CONTENT_TYPE = "text/csv;charset=UTF-8";

    /** CSV 文件扩展名（含点）。 */
    public static final String CSV_EXTENSION = ".csv";

    /** CSV 行分隔符：RFC 4180 规定为 CRLF。 */
    public static final String LINE_SEPARATOR = "\r\n";

    /** 会被表格软件当公式解析的危险首字符。 */
    private static final char[] FORMULA_PREFIXES = {'=', '+', '-', '@', '\t', '\r'};

    /**
     * 时间列格式。与项目全局 JSON 时间格式（{@code yyyy-MM-dd HH:mm:ss}）保持一致，
     * 使导出的 CSV 与接口返回的时间文本形态相同。
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private CsvUtils() {
    }

    // ------------------------------------------------------------------ 纯函数

    /**
     * 组装 CSV：首部 BOM，行尾 CRLF，每个单元格先中和公式注入再按 RFC 4180 转义。
     *
     * @param headers 表头（不可为 {@code null}；元素不可为 {@code null}）
     * @param rows    数据行；单元格可为 {@code null} 或空串（输出为空串）
     * @return CSV 文本（按 UTF-8 写入响应即为 BOM 字节 EF BB BF）
     */
    public static String toCsv(List<String> headers, List<List<@Nullable String>> rows) {
        StringBuilder csv = new StringBuilder(UTF8_BOM);
        appendRow(csv, headers);
        for (List<@Nullable String> row : rows) {
            appendRow(csv, row);
        }
        return csv.toString();
    }

    /**
     * 单个单元格处理：公式注入中和 + CSV 转义。
     *
     * @param value 原始值（可为 {@code null}）
     * @return 可直接拼进 CSV 的单元格
     */
    public static String escapeCell(@Nullable String value) {
        String neutralized = neutralizeFormula(value);
        return needsQuoting(neutralized) ? quote(neutralized) : neutralized;
    }

    /**
     * 空值安全的导出时间格式化（时间列统一形态）。
     *
     * @param value 时间（可空）
     * @return {@code yyyy-MM-dd HH:mm:ss}；{@code null} 返回空串
     */
    public static String formatDateTime(@Nullable LocalDateTime value) {
        return value == null ? "" : DATE_TIME_FORMATTER.format(value);
    }

    // ------------------------------------------------------------------ 导出到 HTTP 响应

    /**
     * 导出 CSV 文本到 HTTP 响应（浏览器下载），自动处理 BOM 字节、内容类型与文件名编码。
     *
     * <p>BOM 由 {@link #toCsv} 以字符 {@code \uFEFF} 打头，此处按 UTF-8 编码写出即得到
     * {@code EF BB BF} 三字节，表格软件据此判定 UTF-8。</p>
     *
     * @param response HTTP 响应
     * @param fileName 文件名（不含扩展名）
     * @param csv      CSV 文本（通常来自 {@link #toCsv}）
     */
    public static void export(HttpServletResponse response, String fileName, String csv) {
        DownloadResponses.prepare(response, fileName, CSV_CONTENT_TYPE, CSV_EXTENSION);
        try {
            write(response.getOutputStream(), csv);
        } catch (IOException e) {
            throw new IllegalStateException("CSV 导出失败：" + fileName, e);
        }
    }

    /**
     * 将 CSV 文本按 UTF-8 写入输出流（不涉及响应头；BOM 已包含在文本首部）。
     *
     * @param outputStream 输出流
     * @param csv          CSV 文本
     */
    public static void write(OutputStream outputStream, String csv) {
        try {
            outputStream.write(csv.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("CSV 写入失败", e);
        }
    }

    // ------------------------------------------------------------------ 流式导出

    /**
     * 流式写出 CSV：首部 BOM，逐行转义、写出，每行写出后立即 {@code flush}。
     *
     * <p><b>与 {@link #write(OutputStream, String)} 的区别（重载不冲突，语义不同）</b>：本方法接收
     * 表头与数据行<b>边遍历边写</b>，不把全部内容拼成一个 {@code String}，堆内存占用只与单行宽度相关，
     * 适合数万行以上的大导出；后者接收已经拼好的整段文本（通常来自 {@link #toCsv}），适合小数据量或
     * 需要复用 CSV 文本的场景。每行 {@code flush} 的代价是底层写调用次数随行数增长，收益是下游（HTTP
     * 客户端）能尽早收到数据、且不会在内存里堆积整份内容。</p>
     *
     * <p><b>与 {@link #toCsv} 逐字节一致</b>：同样先写 UTF-8 BOM（编码后为 {@code EF BB BF}），
     * 逐单元格执行同一套 {@link #escapeCell}（公式注入中和 + RFC 4180 转义），行尾同样为
     * {@link #LINE_SEPARATOR CRLF}。即 {@code write(out, headers, rows)} 与
     * {@code write(out, toCsv(headers, rows))} 的输出字节完全相同。</p>
     *
     * <p><b>空值与空集合</b>：{@code headers}、{@code rows} 本身不可为 {@code null}；数据行与单元格
     * 可为 {@code null}（{@code null} 单元格输出为空串）；{@code rows} 为空集合时至少输出 BOM 与表头。</p>
     *
     * <p><b>资源归属</b>：本方法<b>不关闭</b> {@code outputStream}，关闭责任归调用方（与
     * {@link #write(OutputStream, String)} 一致）。写入失败时以 {@link IllegalStateException}
     * 包装原始 {@link IOException} 抛出，不做静默吞掉或部分成功假装成功。</p>
     *
     * @param outputStream 输出流（本方法不关闭它）
     * @param headers      表头（不可为 {@code null}；元素不可为 {@code null}）
     * @param rows         数据行（不可为 {@code null}，可为空集合；行内单元格可为 {@code null}）
     */
    public static void write(
        OutputStream outputStream, List<String> headers, Iterable<List<@Nullable String>> rows) {
        Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        try {
            writer.write(UTF8_BOM);
            writeRow(writer, headers);
            writer.flush();
            for (List<@Nullable String> row : rows) {
                writeRow(writer, row);
                writer.flush();
            }
        } catch (IOException e) {
            throw new IllegalStateException("CSV 流式写入失败", e);
        }
    }

    /**
     * 流式导出 CSV 到 HTTP 响应（浏览器下载）。
     *
     * <p>响应头处理（{@link #CSV_CONTENT_TYPE}、RFC 5987 编码的文件名、{@link #CSV_EXTENSION}）与
     * {@link #export(HttpServletResponse, String, String)} 完全一致，唯一区别是数据经
     * {@link #write(OutputStream, List, Iterable)} 流式写出而非先拼成整段文本；两者对同一份数据
     * 产生相同的响应体字节。</p>
     *
     * @param response HTTP 响应
     * @param fileName 文件名（不含扩展名）
     * @param headers  表头（不可为 {@code null}；元素不可为 {@code null}）
     * @param rows     数据行（不可为 {@code null}，可为空集合；行内单元格可为 {@code null}）
     */
    public static void export(
        HttpServletResponse response, String fileName, List<String> headers, Iterable<List<@Nullable String>> rows) {
        DownloadResponses.prepare(response, fileName, CSV_CONTENT_TYPE, CSV_EXTENSION);
        try {
            write(response.getOutputStream(), headers, rows);
        } catch (IOException e) {
            throw new IllegalStateException("CSV 导出失败：" + fileName, e);
        }
    }

    // ------------------------------------------------------------------ 内部实现

    private static void appendRow(StringBuilder csv, List<@Nullable String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(escapeCell(cells.get(i)));
        }
        csv.append(LINE_SEPARATOR);
    }

    /**
     * 与 {@link #appendRow} 完全同构的流式版本：同一转义、同一分隔符、同一 CRLF 行尾，
     * 保证两条路径逐字节一致（差异只在「拼 String」与「写 Writer」）。
     */
    private static void writeRow(Writer writer, List<@Nullable String> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                writer.write(',');
            }
            writer.write(escapeCell(cells.get(i)));
        }
        writer.write(LINE_SEPARATOR);
    }

    /** 单元格是否需按 RFC 4180 加引号：含分隔符、引号或换行。 */
    private static boolean needsQuoting(String value) {
        return value.indexOf(',') >= 0
            || value.indexOf('"') >= 0
            || value.indexOf('\n') >= 0
            || value.indexOf('\r') >= 0;
    }

    /** RFC 4180 转义：整体加双引号，内部双引号翻倍。 */
    private static String quote(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /**
     * 危险首字符（{@code = + - @ TAB CR}）前缀单引号；其余原样。
     *
     * <p>只看首字符：表格软件判定「是否为公式」的依据就是单元格第一个字符。已有的单引号前缀不会被
     * 重复处理（首字符已是 {@code '} 时不在危险集合内）。</p>
     *
     * @param value 原始值（可为 {@code null}）
     * @return 中和后的值；{@code null} 与空串均返回空串
     */
    private static String neutralizeFormula(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char first = value.charAt(0);
        for (char prefix : FORMULA_PREFIXES) {
            if (first == prefix) {
                return "'" + value;
            }
        }
        return value;
    }
}

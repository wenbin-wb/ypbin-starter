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

import cn.ypbin.starter.core.exception.BusinessException;
import cn.ypbin.starter.tools.web.DownloadResponses;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import org.apache.fesod.sheet.ExcelWriter;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.read.builder.ExcelReaderBuilder;
import org.apache.fesod.sheet.read.builder.ExcelReaderSheetBuilder;
import org.apache.fesod.sheet.read.listener.PageReadListener;
import org.apache.fesod.sheet.read.listener.ReadListener;
import org.apache.fesod.sheet.write.metadata.WriteSheet;
import org.apache.fesod.sheet.write.style.column.LongestMatchColumnWidthStyleStrategy;
import org.jspecify.annotations.Nullable;

/**
 * Excel 读写工具。
 *
 * <p>基于 Apache Fesod 2.0+ 的注解驱动读写：实体字段用 {@code @ExcelProperty} 标注列名。覆盖常见场景——
 * 同步读 / 指定 sheet 与表头行读 / 大文件分批流式读；单 sheet 写 / 自适应列宽写 / 多 sheet 写；
 * 导出到 HTTP 响应（数据导出 / 空模板导出），并自动处理文件名编码与响应头。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public final class ExcelUtils {

    private static final String XLSX = ".xlsx";

    /**
     * 同步读取的默认行数上限：超过即抛 {@link BusinessException}。
     *
     * <p>{@code doReadSync} 会把整个 sheet 读进堆，上传入口一旦接上该方法就等于把 OOM 暴露给外部
     * 请求。上限存在的意义是「宁可报错也不 OOM」；需要更大数据量请用 {@link #readInBatch} 流式分批。</p>
     */
    public static final int DEFAULT_MAX_ROWS = 100_000;

    private static final String EXCEL_CONTENT_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private ExcelUtils() {
    }

    // ------------------------------------------------------------------ 读

    /**
     * 从输入流读取第一个 sheet 为对象列表（同步全量读取）。
     *
     * @param inputStream Excel 输入流
     * @param clazz       目标类型（字段用 @ExcelProperty 标注）
     * @param <T>         类型
     * @return 数据列表
     */
    public static <T> List<T> read(InputStream inputStream, Class<T> clazz) {
        return readWithLimit(inputStream, clazz, null, null, DEFAULT_MAX_ROWS);
    }

    /**
     * 读取并限制最大行数（流式读取，命中上限立即中止，不会先把整个文件读进堆）。
     *
     * @param inputStream Excel 输入流
     * @param clazz       目标类型
     * @param maxRows     最大行数（必须 &gt;= 1）
     * @param <T>         类型
     * @return 数据列表
     */
    public static <T> List<T> readLimited(InputStream inputStream, Class<T> clazz, int maxRows) {
        return readWithLimit(inputStream, clazz, null, null, maxRows);
    }

    /**
     * 读取指定 sheet、自定义表头行数，并限制最大行数。
     *
     * @param inputStream   Excel 输入流
     * @param clazz         目标类型
     * @param sheetNo       sheet 序号（从 0 开始）
     * @param headRowNumber 表头占用的行数
     * @param maxRows       最大行数（必须 &gt;= 1）
     * @param <T>           类型
     * @return 数据列表
     */
    public static <T> List<T> readLimited(InputStream inputStream, Class<T> clazz, int sheetNo,
                                          int headRowNumber, int maxRows) {
        return readWithLimit(inputStream, clazz, sheetNo, headRowNumber, maxRows);
    }

    /**
     * 同步读取的公共实现：以流式监听器收集，超过 {@code maxRows} 立即抛
     * {@link BusinessException} 中止。
     */
    private static <T> List<T> readWithLimit(InputStream inputStream, Class<T> clazz,
                                             @Nullable Integer sheetNo, @Nullable Integer headRowNumber, int maxRows) {
        if (maxRows <= 0) {
            throw new IllegalArgumentException("maxRows 必须 >= 1（当前 " + maxRows + "）");
        }
        List<T> rows = new ArrayList<>();
        ReadListener<T> listener = new ReadListener<>() {
            @Override
            public void invoke(T data, AnalysisContext context) {
                if (rows.size() >= maxRows) {
                    throw new BusinessException("Excel 行数超过上限 " + maxRows + " 行，请拆分文件或改用 readInBatch 分批读取");
                }
                rows.add(data);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext context) {
                // 无需额外处理：数据已在 invoke 中收集
            }
        };
        ExcelReaderBuilder builder = FesodSheet.read(inputStream, clazz, listener);
        ExcelReaderSheetBuilder sheet = sheetNo == null ? builder.sheet() : builder.sheet(sheetNo);
        if (headRowNumber != null) {
            sheet.headRowNumber(headRowNumber);
        }
        sheet.doRead();
        return rows;
    }

    /**
     * 读取指定序号的 sheet（同步全量读取）。
     *
     * @param inputStream Excel 输入流
     * @param clazz       目标类型
     * @param sheetNo     sheet 序号（从 0 开始）
     * @param <T>         类型
     * @return 数据列表
     */
    public static <T> List<T> read(InputStream inputStream, Class<T> clazz, int sheetNo) {
        return readWithLimit(inputStream, clazz, sheetNo, null, DEFAULT_MAX_ROWS);
    }

    /**
     * 读取指定 sheet 并自定义表头行数（同步全量读取）。
     *
     * @param inputStream  Excel 输入流
     * @param clazz        目标类型
     * @param sheetNo      sheet 序号（从 0 开始）
     * @param headRowNumber 表头占用的行数（数据从其后开始）
     * @param <T>          类型
     * @return 数据列表
     */
    public static <T> List<T> read(InputStream inputStream, Class<T> clazz, int sheetNo, int headRowNumber) {
        return readWithLimit(inputStream, clazz, sheetNo, headRowNumber, DEFAULT_MAX_ROWS);
    }

    /**
     * 大文件分批流式读取：每积累 {@code batchSize} 行回调一次，避免一次性载入内存。
     *
     * @param inputStream   Excel 输入流
     * @param clazz         目标类型
     * @param batchSize     每批行数
     * @param batchConsumer 每批数据的处理回调
     * @param <T>           类型
     */
    public static <T> void readInBatch(InputStream inputStream, Class<T> clazz, int batchSize,
                                       Consumer<List<T>> batchConsumer) {
        FesodSheet.read(inputStream, clazz, new PageReadListener<T>(batchConsumer, batchSize)).sheet().doRead();
    }

    // ------------------------------------------------------------------ 写

    /**
     * 将数据列表写入输出流的单个 sheet（默认开启中英文自适应列宽策略）。
     *
     * @param outputStream 输出流
     * @param sheetName    工作表名
     * @param clazz        数据类型
     * @param data         数据
     * @param <T>          类型
     */
    public static <T> void write(OutputStream outputStream, String sheetName, Class<T> clazz, List<T> data) {
        FesodSheet.write(outputStream, clazz)
            .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
            .sheet(sheetName)
            .doWrite(data);
    }

    /**
     * 写入时仅导出指定列（includeColumns，字段名集合）。
     *
     * @param outputStream   输出流
     * @param sheetName      工作表名
     * @param clazz          数据类型
     * @param data           数据
     * @param includeColumns 需要导出的字段名集合
     * @param <T>            类型
     */
    public static <T> void writeIncludeColumns(OutputStream outputStream, String sheetName, Class<T> clazz,
                                               List<T> data, Collection<String> includeColumns) {
        FesodSheet.write(outputStream, clazz)
            .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
            .includeColumnFieldNames(includeColumns)
            .sheet(sheetName)
            .doWrite(data);
    }

    /**
     * 写入时排除指定列（excludeColumns，字段名集合）。
     *
     * @param outputStream   输出流
     * @param sheetName      工作表名
     * @param clazz          数据类型
     * @param data           数据
     * @param excludeColumns 需要排除的字段名集合
     * @param <T>            类型
     */
    public static <T> void writeExcludeColumns(OutputStream outputStream, String sheetName, Class<T> clazz,
                                               List<T> data, Collection<String> excludeColumns) {
        FesodSheet.write(outputStream, clazz)
            .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
            .excludeColumnFieldNames(excludeColumns)
            .sheet(sheetName)
            .doWrite(data);
    }

    /**
     * 一个工作簿写入多个 sheet。每个 sheet 的名称、类型、数据由 {@link SheetData} 描述。
     *
     * @param outputStream 输出流
     * @param sheets       多个 sheet 的数据描述
     */
    public static void writeMultiSheet(OutputStream outputStream, List<SheetData<?>> sheets) {
        try (ExcelWriter writer = FesodSheet.write(outputStream)
            .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
            .build()) {
            int index = 0;
            for (SheetData<?> sheet : sheets) {
                WriteSheet writeSheet = FesodSheet.writerSheet(index++, sheet.sheetName())
                    .head(sheet.clazz()).build();
                writer.write(sheet.data(), writeSheet);
            }
        }
    }

    // ------------------------------------------------------------------ 导出到 HTTP 响应

    /**
     * 导出单 sheet 到 HTTP 响应（浏览器下载），自动处理文件名编码与响应头。
     *
     * @param response HTTP 响应
     * @param fileName 文件名（不含扩展名）
     * @param clazz    数据类型
     * @param data     数据
     * @param <T>      类型
     */
    public static <T> void export(HttpServletResponse response, String fileName, Class<T> clazz, List<T> data) {
        prepareResponse(response, fileName);
        try {
            write(response.getOutputStream(), fileName, clazz, data);
        } catch (IOException e) {
            throw new IllegalStateException("Excel 导出失败：" + fileName, e);
        }
    }

    /**
     * 导出 Excel 导入空模板到 HTTP 响应（仅输出标准表头，供用户下载后填写导入）。
     *
     * @param response HTTP 响应
     * @param fileName 模板文件名（不含扩展名）
     * @param clazz    数据类型
     * @param <T>      类型
     */
    public static <T> void exportTemplate(HttpServletResponse response, String fileName, Class<T> clazz) {
        export(response, fileName, clazz, List.of());
    }

    /**
     * 导出多 sheet 到 HTTP 响应（浏览器下载）。
     *
     * @param response HTTP 响应
     * @param fileName 文件名（不含扩展名）
     * @param sheets   多个 sheet 的数据描述
     */
    public static void exportMultiSheet(HttpServletResponse response, String fileName, List<SheetData<?>> sheets) {
        prepareResponse(response, fileName);
        try {
            writeMultiSheet(response.getOutputStream(), sheets);
        } catch (IOException e) {
            throw new IllegalStateException("Excel 导出失败：" + fileName, e);
        }
    }

    /**
     * 设置 Excel 下载所需的响应头（Content-Type、编码、Content-Disposition 文件名）。
     *
     * @param response HTTP 响应
     * @param fileName 文件名（不含扩展名）
     */
    private static void prepareResponse(HttpServletResponse response, String fileName) {
        // 复用 tools 模块唯一的下载响应头实现：CSV 导出（tools 的 CsvUtils）走同一份代码，避免两处漂移
        DownloadResponses.prepare(response, fileName, EXCEL_CONTENT_TYPE, XLSX);
    }

    /**
     * 多 sheet 导出的单个 sheet 数据描述。
     *
     * @param sheetName sheet 名称
     * @param clazz     数据类型
     * @param data      数据
     * @param <T>       类型
     */
    public record SheetData<T>(String sheetName, Class<T> clazz, List<T> data) {

        /**
         * 便捷工厂方法。
         *
         * @param sheetName sheet 名称
         * @param clazz     数据类型
         * @param data      数据
         * @param <T>       类型
         * @return sheet 数据描述
         */
        public static <T> SheetData<T> of(String sheetName, Class<T> clazz, List<T> data) {
            return new SheetData<>(sheetName, clazz, data);
        }
    }
}

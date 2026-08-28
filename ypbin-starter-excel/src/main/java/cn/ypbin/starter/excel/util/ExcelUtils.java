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

import cn.idev.excel.ExcelWriter;
import cn.idev.excel.FastExcel;
import cn.idev.excel.read.listener.PageReadListener;
import cn.idev.excel.write.metadata.WriteSheet;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Excel 读写工具。
 *
 * <p>基于 FastExcel 的注解驱动读写：实体字段用 {@code @ExcelProperty} 标注列名。覆盖常见场景——
 * 同步读 / 指定 sheet 与表头行读 / 大文件分批流式读；单 sheet 写 / 指定 sheet 写 / 多 sheet 写；
 * 导出到 HTTP 响应（单 sheet / 多 sheet），并自动处理文件名编码与响应头。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public final class ExcelUtils {

    private static final String XLSX = ".xlsx";

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
        return FastExcel.read(inputStream).head(clazz).sheet().doReadSync();
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
        return FastExcel.read(inputStream).head(clazz).sheet(sheetNo).doReadSync();
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
        return FastExcel.read(inputStream).head(clazz).sheet(sheetNo).headRowNumber(headRowNumber).doReadSync();
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
        FastExcel.read(inputStream, clazz, new PageReadListener<T>(batchConsumer, batchSize)).sheet().doRead();
    }

    // ------------------------------------------------------------------ 写

    /**
     * 将数据列表写入输出流的单个 sheet。
     *
     * @param outputStream 输出流
     * @param sheetName    工作表名
     * @param clazz        数据类型
     * @param data         数据
     * @param <T>          类型
     */
    public static <T> void write(OutputStream outputStream, String sheetName, Class<T> clazz, List<T> data) {
        FastExcel.write(outputStream, clazz).sheet(sheetName).doWrite(data);
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
        FastExcel.write(outputStream, clazz).includeColumnFieldNames(includeColumns)
            .sheet(sheetName).doWrite(data);
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
        FastExcel.write(outputStream, clazz).excludeColumnFieldNames(excludeColumns)
            .sheet(sheetName).doWrite(data);
    }

    /**
     * 一个工作簿写入多个 sheet。每个 sheet 的名称、类型、数据由 {@link SheetData} 描述。
     *
     * @param outputStream 输出流
     * @param sheets       多个 sheet 的数据描述
     */
    public static void writeMultiSheet(OutputStream outputStream, List<SheetData<?>> sheets) {
        try (ExcelWriter writer = FastExcel.write(outputStream).build()) {
            int index = 0;
            for (SheetData<?> sheet : sheets) {
                WriteSheet writeSheet = FastExcel.writerSheet(index++, sheet.sheetName())
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
        response.setContentType(EXCEL_CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + encoded + XLSX);
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

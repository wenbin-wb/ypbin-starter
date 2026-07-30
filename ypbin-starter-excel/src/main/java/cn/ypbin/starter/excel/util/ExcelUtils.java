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

import cn.idev.excel.FastExcel;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Excel 读写工具。
 *
 * <p>基于 FastExcel 的注解驱动读写：实体字段用 {@code @ExcelProperty} 标注列名，
 * 即可一行代码完成导入/导出。导出到 HTTP 响应时自动处理文件名编码与响应头。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public final class ExcelUtils {

    private static final String XLSX = ".xlsx";

    private ExcelUtils() {
    }

    /**
     * 从输入流读取 Excel 为对象列表（同步全量读取）。
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
     * 将数据列表写入输出流。
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
     * 导出 Excel 到 HTTP 响应（浏览器下载）。
     *
     * @param response HTTP 响应
     * @param fileName 文件名（不含扩展名）
     * @param clazz    数据类型
     * @param data     数据
     * @param <T>      类型
     */
    public static <T> void export(HttpServletResponse response, String fileName, Class<T> clazz, List<T> data) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
            response.setHeader("Content-Disposition",
                "attachment;filename*=utf-8''" + encoded + XLSX);
            write(response.getOutputStream(), fileName, clazz, data);
        } catch (IOException e) {
            throw new IllegalStateException("Excel 导出失败：" + fileName, e);
        }
    }
}

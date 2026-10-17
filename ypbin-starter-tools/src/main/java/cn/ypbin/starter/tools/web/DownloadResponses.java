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
package cn.ypbin.starter.tools.web;

import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件下载响应头准备（{@code Content-Type} 与 RFC 5987 编码的 {@code Content-Disposition}）。
 *
 * <p><b>为什么单独抽出来</b>：{@code ypbin-starter-excel} 的 Excel 导出与本模块的 CSV 导出都需要
 * 「中文文件名 → 可放进 HTTP 头的百分号编码」。这段逻辑一旦各写一份，任何一处漏掉 {@code +} →
 * {@code %20} 的替换，都会让含空格的文件名在浏览器端变成加号或直接乱码，且<b>只在特定文件名下复现</b>
 * ——属于最难排查的一类漂移。故本类为 starter 内<b>唯一实现</b>，由
 * {@code cn.ypbin.starter.tools.csv.CsvUtils} 与
 * {@code cn.ypbin.starter.excel.util.ExcelUtils} 共用。</p>
 *
 * <p><b>为什么是 public</b>：它需要被 {@code ypbin-starter-excel} 跨模块复用；把下载响应头拼装
 * 留在 excel 侧就等于保留第二份实现，正是要消除的漂移源。它是稳定的低层工具，不是业务契约。</p>
 *
 * <p><b>已知边界（不在此处静默修正）</b>：{@link URLEncoder} 是
 * {@code application/x-www-form-urlencoded} 语义，其「未编码字符集」与 RFC 5987 的
 * {@code attr-char} 并非完全一致（例如 {@code *} 不会被编码）。本类<b>保持与既有实现逐字节一致</b>
 * ——修正它等于改变已发布 Excel 导出的下载行为，应作为独立变更评估，而非搭在本任务里。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
public final class DownloadResponses {

    private DownloadResponses() {
    }

    /**
     * 准备下载响应：设置内容类型、字符编码与附件文件名响应头。
     *
     * <p>调用顺序（先 {@code setContentType} 再 {@code setCharacterEncoding}）与既有 Excel 导出实现
     * 完全一致，容器据此在 {@code Content-Type} 上补 {@code charset=UTF-8}。</p>
     *
     * @param response    HTTP 响应
     * @param fileName    文件名（不含扩展名）
     * @param contentType 内容类型
     * @param extension   扩展名（含点，如 {@code .xlsx}）
     */
    public static void prepare(HttpServletResponse response, String fileName, String contentType, String extension) {
        response.setContentType(contentType);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", contentDisposition(fileName, extension));
    }

    /**
     * 构造 {@code Content-Disposition} 头值（{@code attachment;filename*=utf-8''<百分号编码><扩展名>}）。
     *
     * @param fileName  文件名（不含扩展名，可含中文与空格）
     * @param extension 扩展名（含点）
     * @return 响应头值（纯 ASCII）
     */
    static String contentDisposition(String fileName, String extension) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment;filename*=utf-8''" + encoded + extension;
    }
}

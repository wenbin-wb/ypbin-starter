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

import java.util.List;

/**
 * 一次 CSV 解析的结果：成功记录 + 逐行错误。
 *
 * <p><b>为什么两者同时返回</b>：批量导入的常见诉求是「能导入的先导入，失败的行聚合成报告让用户改完重传」。
 * 只返回成功行会让业务侧无从生成失败报告；只抛异常会让「1 万行错 1 行」变成整批重来。
 * 两者并存，由调用方用 {@link #hasErrors()} 决定策略——需要「全对才要」时改用
 * {@link CsvReader#read}。</p>
 *
 * <p>两个列表都<b>不为 null</b>（空结果返回空列表）。</p>
 *
 * @param rows   成功解析的记录（顺序即文件顺序）
 * @param errors 逐行错误（顺序即文件顺序；同一行只保留首个错误）
 * @author wenbin
 * @since 2026-10-18
 */
public record CsvDocument(List<CsvRow> rows, List<CsvRowError> errors) {

    /**
     * 防御性复制。
     */
    public CsvDocument {
        rows = List.copyOf(rows);
        errors = List.copyOf(errors);
    }

    /**
     * 是否存在行级错误。
     *
     * @return 有错误返回 {@code true}
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}

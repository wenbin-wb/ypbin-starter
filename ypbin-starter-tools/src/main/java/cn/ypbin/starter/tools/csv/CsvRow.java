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
 * 一条 CSV 记录。
 *
 * <p><b>为什么行号是必需信息</b>：导入失败报告必须能让用户「找到那一行」——用户看的是编辑器里的
 * 物理行号，不是「第 N 条记录」。当引号字段跨行时，记录序号与物理行号不再相同，故两者分开：
 * {@code lineNumber} 是该记录<b>起始</b>的物理行号（1 起），跨行字段的记录行号仍是它的第一行。</p>
 *
 * <p><b>单元格不做 trim</b>：解析层不替用户决定「首尾空白是否有意义」（业务侧的编码、名称可能真的
 * 需要保留空白；而 Excel 也会自作主张）。要裁剪由业务层显式做，避免「解析层悄悄改了值」。</p>
 *
 * @param lineNumber 记录起始物理行号（1 起）
 * @param cells      单元格取值（顺序即列顺序；<b>不为 null</b>，空字段为空串而非 null）
 * @author wenbin
 * @since 2026-10-18
 */
public record CsvRow(int lineNumber, List<String> cells) {

    /**
     * 校验并防御性复制。
     */
    public CsvRow {
        cells = List.copyOf(cells);
    }
}

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

import java.io.Serial;

/**
 * CSV 超出资源上限（字节数 / 记录数 / 列数）。
 *
 * <p>单独成类而不复用 {@link CsvParseException}：超限与语法错误对调用方的处置完全不同——
 * 语法错误可以逐行提示让用户改内容，超限只能提示「文件太大/行数太多」并让用户拆分，
 * 且它<b>必然</b>是文件级失败（无部分结果可交付）。分开的异常类型让调用方不必解析错误码
 * 就能正确分流。</p>
 *
 * <p><b>禁静默截断</b>是本异常的立身之本：任何「超限就只取前 N 条」的实现都是静默数据丢失。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
public class CsvLimitExceededException extends CsvParseException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 构造。
     *
     * @param errorCode 具体是哪个上限被突破（{@link CsvErrorCode#FILE_TOO_LARGE} /
     *                  {@link CsvErrorCode#TOO_MANY_ROWS} / {@link CsvErrorCode#TOO_MANY_COLUMNS}）
     * @param message   说明（含实际值与上限值，便于用户判断要拆到多小）
     */
    public CsvLimitExceededException(CsvErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}

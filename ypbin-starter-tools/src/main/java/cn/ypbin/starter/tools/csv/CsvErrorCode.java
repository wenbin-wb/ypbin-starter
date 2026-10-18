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

/**
 * CSV 解析错误码。
 *
 * <p>用枚举而不是字符串：业务侧要把逐行错误聚合成「第 3 行：引号未闭合」这类导入报告，
 * 错误码必须是可枚举、可比较、可国际化的稳定标识；裸字符串会让「同一个错误两种写法」无法聚合，
 * 也容易被当作文案随意改写而破坏下游判断。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
public enum CsvErrorCode {

    /** 文件字节数超过上限（文件级，整批拒绝）。 */
    FILE_TOO_LARGE,

    /** 记录数超过上限（文件级，整批拒绝）。 */
    TOO_MANY_ROWS,

    /** 单条记录的列数超过上限（文件级，整批拒绝）。 */
    TOO_MANY_COLUMNS,

    /** 内容不是合法的 UTF-8（文件级，整批拒绝；严格解码，不做 U+FFFD 静默替换）。 */
    FILE_NOT_UTF8,

    /** 引号未闭合（行级）。 */
    UNCLOSED_QUOTE,

    /** 引号出现在非字段开头的位置（行级，如 {@code a"b}）。 */
    MISPLACED_QUOTE,

    /** 引号段结束后出现多余字符（行级，如 {@code "a"b}）。 */
    TRAILING_CHAR_AFTER_QUOTE,

    /** 列数与首条记录（表头基准）不一致（行级）。 */
    COLUMN_COUNT_MISMATCH,

    /** 严格读取（{@link CsvReader#read}）时存在行级错误的聚合码。 */
    ROW_ERRORS
}

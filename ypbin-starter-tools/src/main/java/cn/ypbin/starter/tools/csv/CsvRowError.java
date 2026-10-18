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
 * 逐行解析错误（行号 + 错误码 + 可展示说明）。
 *
 * <p>形态刻意做小：业务侧要把它们聚合成导入报告（「第 3 行：引号未闭合」），再决定是整批拒绝
 * 还是跳过失败行继续导入。带错误码而不是只带文案，是为了让业务侧能<b>稳定判断</b>错误类别
 * （如「列数不齐」可提示改模板，「引号未闭合」提示用 Excel 另存），而不必去匹配中文字符串。</p>
 *
 * @param lineNumber 出错记录起始物理行号（1 起）
 * @param code       错误码
 * @param message    具体说明（可直接展示；不含文件内容以外的敏感信息）
 * @author wenbin
 * @since 2026-10-18
 */
public record CsvRowError(int lineNumber, CsvErrorCode code, String message) {
}

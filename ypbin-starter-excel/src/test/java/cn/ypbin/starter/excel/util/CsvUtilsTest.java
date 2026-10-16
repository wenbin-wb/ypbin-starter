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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link CsvUtils} 单元测试：BOM、RFC 4180 转义、公式注入中和、空值与 HTTP 导出。
 *
 * <p>用例逐条对齐 IoT 侧既有实现的行为（{@code IotCsvExportUtils} 及其单测），
 * 保证上收到 starter 后对外行为不变。</p>
 *
 * @author wenbin
 * @since 2026-10-16
 */
class CsvUtilsTest {

    // ------------------------------------------------------------------ BOM 与整体形态

    @Test
    @DisplayName("★ BOM 为 UTF-8 首部，按 UTF-8 编码写出即为 EF BB BF 三字节")
    void bomShouldBeFirstBytesWhenEncodedAsUtf8() {
        String csv = CsvUtils.toCsv(List.of("设备编码"), List.of(List.of("D-1")));

        assertThat(csv).startsWith(CsvUtils.UTF8_BOM);
        // 关键：BOM 必须是「编码后」的字节序列，而不只是字符串前缀
        assertThat(csv.getBytes(StandardCharsets.UTF_8))
            .startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
    }

    @Test
    @DisplayName("★ 空 rows 仍输出 BOM + 表头，不返回 null/空串")
    void emptyRowsShouldStillEmitBomAndHeader() {
        String csv = CsvUtils.toCsv(List.of("设备编码", "设备名称"), List.of());

        assertThat(csv).isEqualTo(CsvUtils.UTF8_BOM + "设备编码,设备名称\r\n");
    }

    @Test
    @DisplayName("★ 行尾统一 CRLF，且不以裸 LF 结尾")
    void rowsShouldEndWithCrlf() {
        String csv = CsvUtils.toCsv(List.of("A", "B"), List.of(
            List.of("1", "2"),
            List.of("3", "4")));

        assertThat(csv).isEqualTo(CsvUtils.UTF8_BOM + "A,B\r\n1,2\r\n3,4\r\n");
        assertThat(csv).endsWith("\r\n");
        // 除 CRLF 外不得出现裸 LF（Excel/WPS 对混合行尾的兼容性差）
        assertThat(csv.replace("\r\n", "")).doesNotContain("\n").doesNotContain("\r");
    }

    @Test
    @DisplayName("★ 多行顺序与列顺序稳定（首行表头，其后按入参顺序）")
    void multipleRowsShouldPreserveOrder() {
        String csv = CsvUtils.toCsv(List.of("编码"), List.of(
            List.of("first"),
            List.of("second"),
            List.of("third")));

        assertThat(csv).isEqualTo(CsvUtils.UTF8_BOM + "编码\r\nfirst\r\nsecond\r\nthird\r\n");
    }

    @Test
    @DisplayName("★ 中文无 BOM 之外的任何改动（不转义、不乱码）")
    void chineseTextShouldSurviveUntouched() {
        String csv = CsvUtils.toCsv(List.of("设备名称"), List.of(List.of("温湿度传感器-东厂区#3")));

        assertThat(csv).isEqualTo(CsvUtils.UTF8_BOM + "设备名称\r\n温湿度传感器-东厂区#3\r\n");
        assertThat(new String(csv.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8)).isEqualTo(csv);
    }

    // ------------------------------------------------------------------ RFC 4180 转义

    @Test
    @DisplayName("★ 逗号/引号/换行按 RFC 4180 转义（引号翻倍）")
    void cellsWithSeparatorsMustBeQuotedAndEscaped() {
        assertThat(CsvUtils.escapeCell("a,b")).as("逗号包引号").isEqualTo("\"a,b\"");
        assertThat(CsvUtils.escapeCell("he said \"hi\""))
            .as("普通引号：包引号 + 引号翻倍").isEqualTo("\"he said \"\"hi\"\"\"");
        assertThat(CsvUtils.escapeCell("line1\nline2"))
            .as("换行包引号").isEqualTo("\"line1\nline2\"");
        assertThat(CsvUtils.escapeCell("a\"b,c"))
            .as("同时含引号与逗号").isEqualTo("\"a\"\"b,c\"");
        assertThat(CsvUtils.escapeCell("普通值")).isEqualTo("普通值");
        assertThat(CsvUtils.escapeCell("带\"引号\"的值"))
            .as("中文中的引号同样翻倍").isEqualTo("\"带\"\"引号\"\"的值\"");
    }

    @Test
    @DisplayName("★ 含逗号的整行转义后仍只占一列（列数不被撑开）")
    void escapedCommaShouldNotCreateExtraColumns() {
        String csv = CsvUtils.toCsv(List.of("A", "B"), List.of(List.of("x,y", "z")));

        assertThat(csv).isEqualTo(CsvUtils.UTF8_BOM + "A,B\r\n\"x,y\",z\r\n");
    }

    // ------------------------------------------------------------------ 公式注入中和

    @Test
    @DisplayName("★ 公式注入中和：= + - @ TAB CR 开头一律前缀单引号")
    void dangerousFirstCharactersMustBeNeutralized() {
        assertThat(CsvUtils.escapeCell("=1+1")).isEqualTo("'=1+1");
        assertThat(CsvUtils.escapeCell("+SUM(A1)")).isEqualTo("'+SUM(A1)");
        assertThat(CsvUtils.escapeCell("-1")).as("负数也按危险首字符处理").isEqualTo("'-1");
        assertThat(CsvUtils.escapeCell("@cmd")).isEqualTo("'@cmd");
        assertThat(CsvUtils.escapeCell("\tX")).isEqualTo("'\tX");
        assertThat(CsvUtils.escapeCell("\rX")).as("CR 中和后仍需包引号").isEqualTo("\"'\rX\"");
        assertThat(CsvUtils.escapeCell("'=已是文本")).as("已有前缀不重复处理（首字符是单引号）")
            .isEqualTo("'=已是文本");
    }

    @Test
    @DisplayName("★ 真实载荷形态：=cmd|'/C calc'!A1,2 被中和且内部引号/逗号同时正确转义")
    void realWorldInjectionPayloadShouldBeNeutralized() {
        // 先中和（首字符 '=' 前缀单引号），再因含逗号整体加双引号；
        // 载荷里的单引号不是 CSV 转义字符，保持原样，只有双引号才翻倍
        String payload = "=cmd|'/C calc'!A1,2";

        assertThat(CsvUtils.escapeCell(payload)).isEqualTo("\"'=cmd|'/C calc'!A1,2\"");
        assertThat(CsvUtils.escapeCell(payload)).startsWith("\"'=");
        // 带双引号的载荷：中和与引号翻倍同时生效
        assertThat(CsvUtils.escapeCell("=HYPERLINK(\"http://evil\")"))
            .isEqualTo("\"'=HYPERLINK(\"\"http://evil\"\")\"");
    }

    @Test
    @DisplayName("★ 危险字符出现在非首位时不得中和（避免改变正常数据）")
    void dangerousCharacterNotAtFirstPositionShouldBeKept() {
        assertThat(CsvUtils.escapeCell("a=b")).isEqualTo("a=b");
        assertThat(CsvUtils.escapeCell("1-2")).isEqualTo("1-2");
        assertThat(CsvUtils.escapeCell("x@y")).isEqualTo("x@y");
    }

    // ------------------------------------------------------------------ 空值

    @Test
    @DisplayName("★ null 与空串输出空单元格，不抛异常（两字段仍保留分隔符）")
    void nullAndEmptyCellsMustBeBlank() {
        assertThat(CsvUtils.escapeCell(null)).isEmpty();
        assertThat(CsvUtils.escapeCell("")).isEmpty();
        assertThat(CsvUtils.toCsv(List.of("A", "B"), List.of(Arrays.asList(null, ""))))
            .isEqualTo(CsvUtils.UTF8_BOM + "A,B\r\n,\r\n");
    }

    @Test
    @DisplayName("★ 仅含空格或空白字符的单元格原样保留（只有首字符危险才中和）")
    void whitespaceOnlyCellShouldBeKept() {
        assertThat(CsvUtils.escapeCell(" ")).isEqualTo(" ");
        assertThat(CsvUtils.escapeCell("  x  ")).isEqualTo("  x  ");
    }

    // ------------------------------------------------------------------ 时间格式化

    @Test
    @DisplayName("★ 时间列空值安全；正常值固定 yyyy-MM-dd HH:mm:ss")
    void dateTimeMustBeFormattedOrBlank() {
        assertThat(CsvUtils.formatDateTime(null)).isEmpty();
        assertThat(CsvUtils.formatDateTime(LocalDateTime.of(2026, 10, 15, 1, 2, 3)))
            .isEqualTo("2026-10-15 01:02:03");
        assertThat(CsvUtils.formatDateTime(LocalDateTime.of(2026, 1, 2, 3, 4, 5)))
            .as("月/日/时分秒补零").isEqualTo("2026-01-02 03:04:05");
    }

    // ------------------------------------------------------------------ 导出到 HTTP 响应

    @Test
    @DisplayName("★ 导出到响应：BOM 字节、内容类型、RFC 5987 文件名与响应体一致")
    void exportShouldWriteBomBytesAndHeaders() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        String csv = CsvUtils.toCsv(List.of("设备编码"), List.of(List.of("D-1")));

        CsvUtils.export(response, "设备台账", csv);

        // 实测：setContentType 收到含 charset 的完整类型时，getContentType() 原样返回（不剥离参数）
        assertThat(response.getContentType()).isEqualTo(CsvUtils.CSV_CONTENT_TYPE);
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        assertThat(response.getHeader("Content-Disposition"))
            .isEqualTo("attachment;filename*=utf-8''%E8%AE%BE%E5%A4%87%E5%8F%B0%E8%B4%A6.csv");
        assertThat(response.getContentAsByteArray())
            .startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(new String(response.getContentAsByteArray(), StandardCharsets.UTF_8)).isEqualTo(csv);
    }

    @Test
    @DisplayName("★ 含空格文件名编码为 %20 而非 +（与 Excel 导出同一实现）")
    void fileNameWithSpaceShouldUsePercent20() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CsvUtils.export(response, "设备 台账", CsvUtils.toCsv(List.of("A"), List.of()));

        assertThat(response.getHeader("Content-Disposition"))
            .isEqualTo("attachment;filename*=utf-8''%E8%AE%BE%E5%A4%87%20%E5%8F%B0%E8%B4%A6.csv");
        assertThat(response.getHeader("Content-Disposition")).doesNotContain("+");
    }

    @Test
    @DisplayName("★ 纯 ASCII 文件名原样输出")
    void asciiFileNameShouldPassThrough() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CsvUtils.export(response, "iot-devices", CsvUtils.toCsv(List.of("A"), List.of()));

        assertThat(response.getHeader("Content-Disposition"))
            .isEqualTo("attachment;filename*=utf-8''iot-devices.csv");
    }

    @Test
    @DisplayName("★ write(OutputStream) 与 export 的字节完全一致（BOM 不丢）")
    void writeToOutputStreamShouldMatchExport() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String csv = CsvUtils.toCsv(List.of("中文"), List.of(List.of("值")));

        CsvUtils.write(out, csv);

        assertThat(out.toByteArray()).isEqualTo(csv.getBytes(StandardCharsets.UTF_8));
        assertThat(out.toByteArray()).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
    }
}

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link CsvReader} 单元测试：RFC 4180 读取、BOM、行尾混用、列数一致性、上限与逐行错误。
 *
 * <p>用例覆盖 issue #102 验收清单的 (a)~(g)，并额外钉住三件事：</p>
 * <ul>
 *   <li><b>写读一致（round-trip）</b>：{@link CsvUtils#escapeCell} 写出的单元格必须被
 *       {@link CsvReader} 原样读回——写侧与读侧是同一份契约的两半，任何一侧单独改动都会让
 *       「导出的文件再导入」出错，而这种 bug 只有在用户真的回传文件时才暴露；</li>
 *   <li><b>禁静默截断</b>：超限必须抛异常，而不是返回前 N 条；</li>
 *   <li><b>禁静默修正</b>：引号位置非法必须报行级错误，而不是按字面量吞掉。</li>
 * </ul>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class CsvReaderTest {

    // ------------------------------------------------------------------ (a) 引号内的逗号

    @Test
    @DisplayName("(a) 引号内的逗号不是分隔符")
    void quotedCommaShouldNotSplitField() {
        CsvDocument document = CsvReader.parseText("\"a,b\",c\r\n1,2");

        assertThat(document.hasErrors()).isFalse();
        assertThat(document.rows()).hasSize(2);
        assertThat(document.rows().get(0).cells()).containsExactly("a,b", "c");
        assertThat(document.rows().get(1).cells()).containsExactly("1", "2");
    }

    // ------------------------------------------------------------------ (b) 引号内的换行

    @Test
    @DisplayName("(b) 引号内的换行属于字段内容，不切分记录")
    void quotedNewlineShouldStayInField() {
        CsvDocument document = CsvReader.parseText("id,remark\n1,\"第一行\n第二行\"");

        assertThat(document.hasErrors()).isFalse();
        assertThat(document.rows()).hasSize(2);
        assertThat(document.rows().get(1).cells()).containsExactly("1", "第一行\n第二行");
    }

    @Test
    @DisplayName("(b2) 跨行字段之后的记录行号按物理行号递增，仍然指向真实起始行")
    void lineNumberShouldFollowPhysicalLinesAfterMultilineField() {
        // 第 1 行：表头；第 2 行起：一条跨两行的记录；第 4 行：最后一条记录
        CsvDocument document = CsvReader.parseText("a,b\n1,\"x\ny\"\n2,z");

        assertThat(document.hasErrors()).isFalse();
        assertThat(document.rows()).hasSize(3);
        assertThat(document.rows().get(1).lineNumber()).isEqualTo(2);
        // 跨行字段吃掉了一个物理行，故最后一条记录的起始物理行号是 4 而不是 3
        assertThat(document.rows().get(2).lineNumber()).isEqualTo(4);
    }

    // ------------------------------------------------------------------ (c) 连续引号

    @Test
    @DisplayName("(c) 引号内两个连续引号表示一个字面引号")
    void doubledQuoteShouldBecomeSingleQuote() {
        CsvDocument document = CsvReader.parseText("name\n\"他说\"\"好\"\"\"");

        assertThat(document.hasErrors()).isFalse();
        assertThat(document.rows()).hasSize(2);
        assertThat(document.rows().get(1).cells()).containsExactly("他说\"好\"");
    }

    // ------------------------------------------------------------------ (d) UTF-8 BOM

    @Test
    @DisplayName("(d) UTF-8 BOM 被剥离，首个字段不带 \\uFEFF")
    void bomShouldBeStripped() {
        String withBom = CsvUtils.UTF8_BOM + "设备编码,设备名称\r\nD-1,水泵";

        CsvDocument fromText = CsvReader.parseText(withBom);
        CsvDocument fromBytes = CsvReader.parse(withBom.getBytes(StandardCharsets.UTF_8));

        assertThat(fromText.rows().get(0).cells()).containsExactly("设备编码", "设备名称");
        assertThat(fromBytes.rows().get(0).cells()).containsExactly("设备编码", "设备名称");
    }

    @Test
    @DisplayName("(d2) 只有 BOM 的空文件解析为空结果，不产生空记录")
    void bomOnlyFileShouldBeEmpty() {
        CsvDocument document = CsvReader.parse(CsvUtils.UTF8_BOM.getBytes(StandardCharsets.UTF_8));

        assertThat(document.rows()).isEmpty();
        assertThat(document.errors()).isEmpty();
    }

    // ------------------------------------------------------------------ (e) CRLF / LF 混用

    @Test
    @DisplayName("(e) CRLF 与 LF 混用都能正确切分记录")
    void mixedLineEndingsShouldBeAccepted() {
        CsvDocument document = CsvReader.parseText("a,b\r\n1,2\n3,4\r\n5,6");

        assertThat(document.hasErrors()).isFalse();
        assertThat(document.rows()).hasSize(4);
        assertThat(document.rows()).extracting(row -> row.cells().get(0))
            .containsExactly("a", "1", "3", "5");
    }

    @Test
    @DisplayName("(e2) 裸 CR 行尾同样接受（老 Mac 另存的文件）")
    void bareCrShouldBeAccepted() {
        CsvDocument document = CsvReader.parseText("a,b\r1,2");

        assertThat(document.hasErrors()).isFalse();
        assertThat(document.rows()).hasSize(2);
    }

    // ------------------------------------------------------------------ (f) 列数不齐

    @Test
    @DisplayName("(f) 列数与首行不一致的记录记为行级错误，不进入成功记录")
    void raggedColumnCountShouldBeRowError() {
        CsvDocument document = CsvReader.parseText("a,b,c\n1,2,3\n4,5\n6,7,8");

        assertThat(document.hasErrors()).isTrue();
        assertThat(document.rows()).hasSize(3);
        assertThat(document.errors()).hasSize(1);
        CsvRowError error = document.errors().get(0);
        assertThat(error.lineNumber()).isEqualTo(3);
        assertThat(error.code()).isEqualTo(CsvErrorCode.COLUMN_COUNT_MISMATCH);
        assertThat(error.message()).contains("第 3 行").contains("2").contains("3 列");
    }

    @Test
    @DisplayName("(f2) 严格读取遇到列数不齐直接抛异常，并带上行级明细")
    void strictReadShouldThrowOnRaggedColumnCount() {
        assertThatThrownBy(() -> CsvReader.readText("a,b\n1,2\n3"))
            .isInstanceOf(CsvParseException.class)
            .hasMessageContaining("1 处解析错误")
            .satisfies(ex -> {
                CsvParseException parseException = (CsvParseException) ex;
                assertThat(parseException.getErrorCode()).isEqualTo(CsvErrorCode.ROW_ERRORS);
                assertThat(parseException.getErrors()).hasSize(1);
                assertThat(parseException.getErrors().get(0).lineNumber()).isEqualTo(3);
            });
    }

    // ------------------------------------------------------------------ (g) 超限抛异常

    @Test
    @DisplayName("(g) 超过字节上限抛 CsvLimitExceededException，不返回被截断的结果")
    void exceedingByteLimitShouldThrow() {
        byte[] content = "a,b\n1,2".getBytes(StandardCharsets.UTF_8);
        CsvLimits tooSmall = new CsvLimits(content.length - 1, 100, 10);

        assertThatThrownBy(() -> CsvReader.parse(content, tooSmall))
            .isInstanceOf(CsvLimitExceededException.class)
            .hasMessageContaining("超过上限")
            .satisfies(ex -> assertThat(((CsvParseException) ex).getErrorCode())
                .isEqualTo(CsvErrorCode.FILE_TOO_LARGE));
    }

    @Test
    @DisplayName("(g2) 超过记录数上限抛异常（含表头计数）")
    void exceedingRowLimitShouldThrow() {
        // 表头 + 2 条数据行 = 3 条记录，上限设 2
        CsvLimits tooFewRows = new CsvLimits(CsvLimits.DEFAULT_MAX_BYTES, 2, 10);

        assertThatThrownBy(() -> CsvReader.parseText("a,b\n1,2\n3,4", tooFewRows))
            .isInstanceOf(CsvLimitExceededException.class)
            .hasMessageContaining("记录数超过上限")
            .satisfies(ex -> assertThat(((CsvParseException) ex).getErrorCode())
                .isEqualTo(CsvErrorCode.TOO_MANY_ROWS));
    }

    @Test
    @DisplayName("(g3) 超过列数上限抛异常")
    void exceedingColumnLimitShouldThrow() {
        CsvLimits tooFewColumns = new CsvLimits(CsvLimits.DEFAULT_MAX_BYTES, 100, 2);

        assertThatThrownBy(() -> CsvReader.parseText("a,b,c", tooFewColumns))
            .isInstanceOf(CsvLimitExceededException.class)
            .hasMessageContaining("列数上限")
            .satisfies(ex -> assertThat(((CsvParseException) ex).getErrorCode())
                .isEqualTo(CsvErrorCode.TOO_MANY_COLUMNS));
    }

    @Test
    @DisplayName("(g4) 记录数上限统计所有记录（含语法错误行），不会因整批行错误而绕过上限")
    void rowLimitShouldCountErroneousRecordsToo() {
        CsvLimits tooFewRows = new CsvLimits(CsvLimits.DEFAULT_MAX_BYTES, 2, 10);

        // 每行都是「引号段结束后多余字符」，仍是完整的 3 条记录（引号未闭合会吞掉后续换行，不适用本用例）
        assertThatThrownBy(() -> CsvReader.parseText("\"a\"b\n\"a\"b\n\"a\"b", tooFewRows))
            .isInstanceOf(CsvLimitExceededException.class)
            .satisfies(ex -> assertThat(((CsvParseException) ex).getErrorCode())
                .isEqualTo(CsvErrorCode.TOO_MANY_ROWS));
    }

    @Test
    @DisplayName("(g5) 文本入口同样按 UTF-8 字节数校验字节上限（多字节字符不会被低估）")
    void textEntryShouldApplyByteLimitOnUtf8Bytes() {
        // 10 个汉字 = 30 字节（UTF-8），字符数只有 10
        String text = "设备编码设备名称设备编码设备";
        CsvLimits byBytes = new CsvLimits(10, 100, 100);

        assertThatThrownBy(() -> CsvReader.parseText(text, byBytes))
            .isInstanceOf(CsvLimitExceededException.class)
            .satisfies(ex -> assertThat(((CsvParseException) ex).getErrorCode())
                .isEqualTo(CsvErrorCode.FILE_TOO_LARGE));
    }

    // ------------------------------------------------------------------ 非法引号形态

    @Test
    @DisplayName("引号未闭合记为行级错误")
    void unclosedQuoteShouldBeRowError() {
        CsvDocument document = CsvReader.parseText("a,b\n1,\"未闭合");

        assertThat(document.hasErrors()).isTrue();
        assertThat(document.errors().get(0).code()).isEqualTo(CsvErrorCode.UNCLOSED_QUOTE);
        assertThat(document.errors().get(0).lineNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("引号出现在字段中部记为行级错误（不静默按字面量处理）")
    void misplacedQuoteShouldBeRowError() {
        CsvDocument document = CsvReader.parseText("a,b\n1,ab\"cd");

        assertThat(document.hasErrors()).isTrue();
        assertThat(document.errors().get(0).code()).isEqualTo(CsvErrorCode.MISPLACED_QUOTE);
        assertThat(document.errors().get(0).lineNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("引号段结束后有多余字符记为行级错误")
    void trailingCharsAfterQuoteShouldBeRowError() {
        CsvDocument document = CsvReader.parseText("a,b\n1,\"ab\"cd");

        assertThat(document.hasErrors()).isTrue();
        assertThat(document.errors().get(0).code()).isEqualTo(CsvErrorCode.TRAILING_CHAR_AFTER_QUOTE);
    }

    @Test
    @DisplayName("一行只报首个错误，避免同一行产生多条噪音")
    void oneRecordShouldReportOnlyFirstError() {
        CsvDocument document = CsvReader.parseText("a\nab\"c\"d");

        assertThat(document.errors()).hasSize(1);
    }

    // ------------------------------------------------------------------ 编码与空结果

    @Test
    @DisplayName("非法 UTF-8 字节显式失败，而不是静默替换为 U+FFFD")
    void invalidUtf8ShouldThrow() {
        // GBK 编码的「设备」两个字节在 UTF-8 下非法
        byte[] gbkBytes = {(byte) 0xC9, (byte) 0xE8, (byte) 0xB1, (byte) 0xB8};

        assertThatThrownBy(() -> CsvReader.parse(gbkBytes))
            .isInstanceOf(CsvParseException.class)
            .hasMessageContaining("UTF-8")
            .satisfies(ex -> assertThat(((CsvParseException) ex).getErrorCode())
                .isEqualTo(CsvErrorCode.FILE_NOT_UTF8));
    }

    @Test
    @DisplayName("空输入返回空结果（rows/errors 均为空列表，非 null）")
    void emptyInputShouldYieldEmptyDocument() {
        CsvDocument fromNull = CsvReader.parse((byte[]) null);
        CsvDocument fromEmptyBytes = CsvReader.parse(new byte[0]);
        CsvDocument fromNullText = CsvReader.parseText((String) null);
        CsvDocument fromEmptyText = CsvReader.parseText("");

        assertThat(fromNull.rows()).isEmpty();
        assertThat(fromNull.errors()).isEmpty();
        assertThat(fromEmptyBytes.rows()).isEmpty();
        assertThat(fromNullText.rows()).isEmpty();
        assertThat(fromEmptyText.rows()).isEmpty();
        assertThat(CsvReader.read((byte[]) null)).isEmpty();
    }

    @Test
    @DisplayName("空行不产生记录，但记录内的空字段保留为空串")
    void blankLinesShouldBeSkippedButEmptyCellsKept() {
        CsvDocument document = CsvReader.parseText("a,b\n\n1,\n\n");

        assertThat(document.hasErrors()).isFalse();
        assertThat(document.rows()).hasSize(2);
        assertThat(document.rows().get(1).cells()).containsExactly("1", "");
    }

    @Test
    @DisplayName("记录末尾的空字段保留（行尾换行不会被当成额外空记录）")
    void trailingEmptyCellBeforeLineEndShouldBeKept() {
        CsvDocument document = CsvReader.parseText("a,b\r\n1,\r\n");

        assertThat(document.hasErrors()).isFalse();
        assertThat(document.rows()).hasSize(2);
        assertThat(document.rows().get(1).cells()).containsExactly("1", "");
    }

    // ------------------------------------------------------------------ 上限对象自身

    @Test
    @DisplayName("CsvLimits 默认值符合约定，且拒绝非正数上限")
    void limitsShouldValidateAndExposeDefaults() {
        CsvLimits defaults = CsvLimits.defaults();

        assertThat(defaults.maxBytes()).isEqualTo(2 * 1024 * 1024);
        assertThat(defaults.maxRows()).isEqualTo(10_000);
        assertThat(defaults.maxColumns()).isEqualTo(256);
        assertThatThrownBy(() -> new CsvLimits(0, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CsvLimits(1, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CsvLimits(1, 1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------ round-trip 对拍

    @Test
    @DisplayName("★ round-trip：CsvUtils.toCsv 写出的内容能被 CsvReader 原样读回（含逗号/引号/换行/中文）")
    void toCsvOutputShouldRoundTripThroughReader() {
        List<String> headers = List.of("设备编码", "设备名称", "备注");
        List<List<String>> rows = List.of(
            List.of("D-1", "水泵", "普通"),
            List.of("D,2", "泵 \"A\"", "含逗号的编码"),
            List.of("D-3", "多行", "第一行\n第二行"),
            List.of("D-4", "", "空单元格"));

        String csv = CsvUtils.toCsv(headers, rows);
        List<CsvRow> parsed = CsvReader.read(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(parsed).hasSize(rows.size() + 1);
        assertThat(parsed.get(0).cells()).isEqualTo(headers);
        for (int i = 0; i < rows.size(); i++) {
            assertThat(parsed.get(i + 1).cells()).isEqualTo(rows.get(i));
        }
    }

    @Test
    @DisplayName("★ round-trip：逐单元格对拍 escapeCell，读出值等于写侧输入（公式中和除外）")
    void escapeCellShouldRoundTripForEveryCell() {
        List<String> samples = List.of(
            "plain",
            "含逗号,的",
            "含\"引号\"的",
            "含换行\n的",
            "含回车\r的",
            " 首尾有空格 ",
            "",
            "中文与 emoji 😀",
            "a,b\"c\nd",
            "\"整体被引号包住\"");

        for (String sample : samples) {
            // 两列：空串单元格若作为「单列记录的最后一个字段」位于文件末尾，会被当作空行跳过，
            // 无法验证「空串往返」；补一个尾随列让它位于记录中部
            String csv = "v,w\n" + CsvUtils.escapeCell(sample) + ",x";
            CsvDocument document = CsvReader.parseText(csv);

            assertThat(document.hasErrors()).as("单元格 %s 不应产生解析错误", sample).isFalse();
            assertThat(document.rows()).hasSize(2);
            assertThat(document.rows().get(1).cells()).as("单元格 %s 应原样读回", sample)
                .containsExactly(sample, "x");
        }
    }

    @Test
    @DisplayName("★ round-trip：危险首字符被中和后读出带 ' 前缀（安全优先的有意单向变换）")
    void formulaNeutralizationShouldRoundTripAsPrefixedText() {
        List<String> dangerous = List.of("=cmd|'/C calc'!A1", "+1", "-1", "@SUM(A1)", "\tTAB");

        for (String sample : dangerous) {
            String csv = "v\n" + CsvUtils.escapeCell(sample);
            CsvDocument document = CsvReader.parseText(csv);

            assertThat(document.hasErrors()).as("单元格 %s 不应产生解析错误", sample).isFalse();
            assertThat(document.rows().get(1).cells()).as("单元格 %s 应读出带单引号前缀", sample)
                .containsExactly("'" + sample);
        }
    }

    @Test
    @DisplayName("★ round-trip：null 单元格写出为空串，读回为空串（不是 null）")
    void nullCellShouldRoundTripAsEmptyString() {
        String csv = CsvUtils.toCsv(List.of("a", "b"), List.of(java.util.Arrays.asList("x", null)));
        CsvDocument document = CsvReader.parseText(csv);

        assertThat(document.rows()).hasSize(2);
        assertThat(document.rows().get(1).cells()).containsExactly("x", "");
    }

    @Test
    @DisplayName("★ round-trip：流式写出与整段文本写出的内容读回结果一致")
    void streamingWriteShouldRoundTripIdentically() {
        List<String> headers = List.of("id", "值");
        List<List<String>> rows = List.of(List.of("1", "a,b"), List.of("2", "c\"d"));

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        CsvUtils.write(out, headers, rows);

        assertThat(CsvReader.read(out.toByteArray()))
            .extracting(CsvRow::cells)
            .containsExactly(headers, rows.get(0), rows.get(1));
    }

    // ------------------------------------------------------------------ CsvUtils 读侧委托

    @Test
    @DisplayName("CsvUtils 的 parse 委托与 CsvReader 语义一致（不复制实现）")
    void csvUtilsParseShouldDelegateToReader() {
        String csv = "a,b\n1,2";

        assertThat(CsvUtils.parseText(csv).rows())
            .isEqualTo(CsvReader.parseText(csv).rows());
        assertThat(CsvUtils.parse(csv.getBytes(StandardCharsets.UTF_8)).rows())
            .isEqualTo(CsvReader.parse(csv.getBytes(StandardCharsets.UTF_8)).rows());
        assertThat(CsvUtils.parseText(csv, CsvLimits.defaults()).rows()).hasSize(2);
        assertThat(CsvUtils.parse(csv.getBytes(StandardCharsets.UTF_8), CsvLimits.defaults()).rows())
            .hasSize(2);
    }
}

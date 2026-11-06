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
package cn.ypbin.starter.web.handler;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.core.exception.GlobalErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * 把 {@code CONTRACT.md} §3 错误码表变成可执行断言：文档与 {@link GlobalErrorCode} 必须逐条一致。
 *
 * <p>此前漂移过两轮（405 已对外返回却没进码表、415 根本不存在而异常落 500 兜底），
 * 靠人工核对文档不可靠，故由测试守住。</p>
 *
 * @author wenbin
 * @since 2026-11-06
 */
class ContractErrorCodeTest {

    /** §3 码表行：{@code | 400 | 请求参数有误 | ... |}。 */
    private static final Pattern CODE_ROW = Pattern.compile("^\\|\\s*(\\d{3})\\s*\\|[^|]*\\|", Pattern.MULTILINE);

    @Test
    void contractTableShouldMatchGlobalErrorCodeExactly() throws IOException {
        Set<Integer> documented = documentedCodes();

        Set<Integer> implemented = Arrays.stream(GlobalErrorCode.values())
            .map(GlobalErrorCode::getCode)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(documented)
            .as("CONTRACT.md §3 码表与 GlobalErrorCode 必须一致：改一处就要同步另一处")
            .containsExactlyInAnyOrderElementsOf(implemented);
    }

    @Test
    void clientErrorCodesCoveredByContractShouldAllBeDocumented() {
        Set<Integer> documented = documentedCodes();

        // 本次审查暴露的具体缺口：405 曾在码表缺失、415 曾不存在
        assertThat(documented).contains(405, 413, 415);
    }

    private static Set<Integer> documentedCodes() {
        String markdown = readContract();
        Matcher matcher = CODE_ROW.matcher(markdown);
        Set<Integer> codes = new LinkedHashSet<>();
        while (matcher.find()) {
            codes.add(Integer.valueOf(matcher.group(1)));
        }
        assertThat(codes).as("未能在 CONTRACT.md §3 解析出任何错误码，检查文档格式").isNotEmpty();
        return codes;
    }

    private static String readContract() {
        Path current = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 5 && current != null; depth++) {
            Path candidate = current.resolve("CONTRACT.md");
            if (Files.isRegularFile(candidate)) {
                try {
                    return Files.readString(candidate, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new IllegalStateException("读取 CONTRACT.md 失败：" + candidate, e);
                }
            }
            current = current.getParent();
        }
        throw new IllegalStateException("未找到 CONTRACT.md（测试工作目录：" + Path.of("").toAbsolutePath() + "）");
    }
}

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
package cn.ypbin.starter.sensitivewords.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link SensitiveWordService} 单元测试：DFA 检测、替换、词库热更新与边界。
 *
 * @author wenbin
 * @since 2026-08-05
 */
class SensitiveWordServiceTest {

    private final SensitiveWordService service = new SensitiveWordService(List.of("敏感词", "违禁", "bad"));

    @Test
    void contains_shouldDetectHit() {
        assertThat(service.contains("这是一段敏感词内容")).isTrue();
        assertThat(service.contains("含有 bad 英文词")).isTrue();
        assertThat(service.contains("完全正常的文本")).isFalse();
    }

    @Test
    void contains_withNullOrEmpty_shouldReturnFalse() {
        assertThat(service.contains(null)).isFalse();
        assertThat(service.contains("")).isFalse();
    }

    @Test
    void findAll_shouldReturnAllHitsInOrder() {
        List<String> hits = service.findAll("先违禁再敏感词后 bad");

        assertThat(hits).containsExactly("违禁", "敏感词", "bad");
    }

    @Test
    void findAll_withNoHit_shouldReturnEmpty() {
        assertThat(service.findAll("干净文本")).isEmpty();
        assertThat(service.findAll(null)).isEmpty();
    }

    @Test
    void filter_shouldReplaceHitsWithSameLengthMask() {
        String result = service.filter("这是敏感词和违禁内容", '*');

        // 每个命中字符替换为一个掩码字符，长度保持
        assertThat(result).isEqualTo("这是***和**内容");
    }

    @Test
    void filter_withNoHit_shouldReturnOriginal() {
        assertThat(service.filter("干净文本", '*')).isEqualTo("干净文本");
    }

    @Test
    void reload_shouldSwapWordLibrary() {
        assertThat(service.contains("敏感词")).isTrue();

        service.reload(List.of("新词"));

        // 旧词不再命中，新词生效
        assertThat(service.contains("敏感词")).isFalse();
        assertThat(service.contains("含新词")).isTrue();
    }

    @Test
    void reload_shouldIgnoreNullAndBlankWords() {
        service.reload(Arrays.asList("有效", null, "  ", ""));

        assertThat(service.contains("有效")).isTrue();
        assertThat(service.findAll("有效")).containsExactly("有效");
    }
}

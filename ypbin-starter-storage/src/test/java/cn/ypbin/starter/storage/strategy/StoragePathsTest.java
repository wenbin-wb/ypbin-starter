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
package cn.ypbin.starter.storage.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link StoragePaths} URL 编码单元测试。
 *
 * @author wenbin
 * @since 2026-07-30
 */
class StoragePathsTest {

    @Test
    void keepsSlashSeparators() {
        assertThat(StoragePaths.encodePath("2024/01/a.png")).isEqualTo("2024/01/a.png");
    }

    @Test
    void encodesSpaceAsPercent20NotPlus() {
        assertThat(StoragePaths.encodePath("my file.jpg")).isEqualTo("my%20file.jpg");
    }

    @Test
    void encodesPlusSign() {
        assertThat(StoragePaths.encodePath("a+b.txt")).contains("%2B").doesNotContain("+");
    }

    @Test
    void encodesChinese() {
        String encoded = StoragePaths.encodePath("图片/测试.png");
        assertThat(encoded).contains("/").contains("%").endsWith(".png");
    }

    @Test
    void nullOrEmpty() {
        assertThat(StoragePaths.encodePath(null)).isEqualTo("");
        assertThat(StoragePaths.encodePath("")).isEqualTo("");
    }
}

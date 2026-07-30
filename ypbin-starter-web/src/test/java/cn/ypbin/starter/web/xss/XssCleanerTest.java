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
package cn.ypbin.starter.web.xss;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link XssCleaner} 单元测试。
 *
 * @author wenbin
 * @since 2026-07-30
 */
class XssCleanerTest {

    @Test
    void removesScriptTag() {
        String dirty = "hello<script>alert('x')</script>world";
        assertThat(XssCleaner.clean(dirty)).doesNotContain("script").contains("hello", "world");
    }

    @Test
    void removesJavascriptScheme() {
        assertThat(XssCleaner.clean("javascript:alert(1)")).doesNotContain("javascript:");
    }

    @Test
    void removesOnEventAttribute() {
        assertThat(XssCleaner.clean("<img onerror=alert(1)>")).doesNotContain("onerror=");
    }

    @Test
    void normalTextUnchanged() {
        String normal = "用户名: zhang_san, 年龄=18";
        assertThat(XssCleaner.clean(normal)).isEqualTo(normal);
    }

    @Test
    void nullAndBlank_returnedAsIs() {
        assertThat(XssCleaner.clean(null)).isNull();
        assertThat(XssCleaner.clean("")).isEmpty();
    }
}

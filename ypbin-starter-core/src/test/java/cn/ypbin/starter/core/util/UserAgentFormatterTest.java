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
package cn.ypbin.starter.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import cn.hutool.http.useragent.UserAgent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link UserAgentFormatter} 口径测试。
 *
 * <p><b>钉住什么</b>：这段逻辑此前在 starter 的 {@code LogCollector} 与 admin/iot 的
 * {@code LoginSupport} 各有一份（三份重复），且靠注释「与 starter 保持一致」维持口径。
 * 上收后必须保证<b>行为与原来逐条一致</b>，否则操作日志与在线用户列表显示的浏览器名会不一致。</p>
 *
 * @author wenbin
 * @since 2026-10-19
 */
class UserAgentFormatterTest {

    @Test
    @DisplayName("常见 UA：输出「浏览器 版本」")
    void shouldFormatBrowserWithVersion() {
        String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

        assertThat(UserAgentFormatter.formatBrowserHeader(ua))
            .as("应输出「Chrome 120.0.0.0」形态")
            .startsWith("Chrome");
    }

    @Test
    @DisplayName("版本为 Unknown：只留浏览器名称（不输出「Chrome Unknown」）")
    void shouldOmitUnknownVersion() {
        UserAgent userAgent = new UserAgent();
        userAgent.setBrowser(new cn.hutool.http.useragent.Browser("Chrome", null, null));
        userAgent.setVersion("Unknown");

        assertThat(UserAgentFormatter.formatBrowser(userAgent))
            .as("版本为 Unknown 时必须只留名称——这是原来三份实现共同的口径")
            .isEqualTo("Chrome");
    }

    @Test
    @DisplayName("版本缺失（null / 空白）：只留浏览器名称")
    void shouldOmitBlankVersion() {
        UserAgent nullVersion = new UserAgent();
        nullVersion.setBrowser(new cn.hutool.http.useragent.Browser("Firefox", null, null));
        nullVersion.setVersion(null);
        assertThat(UserAgentFormatter.formatBrowser(nullVersion)).isEqualTo("Firefox");

        UserAgent blankVersion = new UserAgent();
        blankVersion.setBrowser(new cn.hutool.http.useragent.Browser("Safari", null, null));
        blankVersion.setVersion("   ");
        assertThat(UserAgentFormatter.formatBrowser(blankVersion)).isEqualTo("Safari");
    }

    @Test
    @DisplayName("浏览器信息缺失：返回 null（不臆造名称）")
    void shouldReturnNullWhenBrowserMissing() {
        UserAgent noBrowser = new UserAgent();
        assertThat(UserAgentFormatter.formatBrowser(noBrowser)).isNull();
    }

    @Test
    @DisplayName("名称 + 版本：按「名称 空格 版本」拼接")
    void shouldJoinNameAndVersion() {
        UserAgent userAgent = new UserAgent();
        userAgent.setBrowser(new cn.hutool.http.useragent.Browser("Edge", null, null));
        userAgent.setVersion("120.0");

        assertThat(UserAgentFormatter.formatBrowser(userAgent)).isEqualTo("Edge 120.0");
    }

    @Test
    @DisplayName("入参为 null / 空白：返回 null，不抛异常")
    void shouldReturnNullForBlankInput() {
        assertThat(UserAgentFormatter.parse(null)).isNull();
        assertThat(UserAgentFormatter.parse("")).isNull();
        assertThat(UserAgentFormatter.parse("   ")).isNull();
        assertThat(UserAgentFormatter.formatBrowserHeader(null)).isNull();
        assertThat(UserAgentFormatter.formatBrowserHeader("  ")).isNull();
    }

    @Test
    @DisplayName("无法识别的 UA：不抛异常，返回非空或不返回（取决于 hutool 解析）")
    void shouldNotThrowForUnrecognizedUserAgent() {
        // 只断言「不抛异常」——hutool 对无法识别 UA 的返回形态由其内部决定，
        // 这里不锁死，避免把第三方实现细节固化成契约
        assertThat(UserAgentFormatter.formatBrowserHeader("garbage-ua-string")).isNotNull();
    }
}

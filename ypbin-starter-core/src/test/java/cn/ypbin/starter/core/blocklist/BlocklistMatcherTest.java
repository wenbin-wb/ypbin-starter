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
package cn.ypbin.starter.core.blocklist;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BlocklistMatcherTest {

    private final BlocklistMatcher matcher = new BlocklistMatcher();

    private final Instant now = Instant.parse("2026-11-04T00:00:00Z");

    @Test
    @DisplayName("按 IP/CIDR/UA/路径四个维度命中第一条规则")
    void matchesAllDimensions() {
        List<BlocklistRule> rules = List.of(
            rule(BlocklistMatchType.IP, "10.0.0.1"),
            rule(BlocklistMatchType.CIDR, "192.168.0.0/16"),
            rule(BlocklistMatchType.USER_AGENT, "Scrapy"),
            rule(BlocklistMatchType.PATH, "/system/open-api"));

        assertThat(match(rules, "10.0.0.1", "Mozilla", "/a")).isPresent();
        assertThat(match(rules, "192.168.3.4", "Mozilla", "/a")).isPresent();
        assertThat(match(rules, "203.0.113.1", "scrapy/2.0", "/a")).isPresent();
        assertThat(match(rules, "203.0.113.1", "Mozilla", "/system/open-api/v1/x")).isPresent();
        assertThat(match(rules, "203.0.113.1", "Mozilla", "/public")).isEmpty();
    }

    @Test
    @DisplayName("过期或禁用的规则不生效")
    void ignoresInactiveRules() {
        BlocklistRule expired = new BlocklistRule("1", BlocklistMatchType.IP, "10.0.0.1",
            now.minusSeconds(1), true, "expired");
        BlocklistRule disabled = new BlocklistRule("2", BlocklistMatchType.IP, "10.0.0.1",
            null, false, "disabled");

        assertThat(match(List.of(expired, disabled), "10.0.0.1", "", "/")).isEmpty();
    }

    @Test
    @DisplayName("路径匹配归一化编码分隔符、大小写与重复斜杠")
    void normalizesPath() {
        List<BlocklistRule> rules = List.of(rule(BlocklistMatchType.PATH, "/system/Internal"));

        assertThat(match(rules, "203.0.113.1", "", "/SYSTEM/INTERNAL/keys")).isPresent();
        assertThat(match(rules, "203.0.113.1", "", "//system//internal%2Fkeys")).isPresent();
        assertThat(match(rules, "203.0.113.1", "", "/system/internalized")).isEmpty();
    }

    @Test
    @DisplayName("放行路径与放行 IP 命中")
    void exclusion() {
        assertThat(matcher.isExcludedPath("/actuator/health/liveness",
            List.of("/actuator/health/**"))).isTrue();
        assertThat(matcher.isExcludedPath("/actuator/env", List.of("/actuator/health"))).isFalse();
        assertThat(matcher.isExcludedIp("127.0.0.1", List.of("127.0.0.1", "::1"))).isTrue();
        assertThat(matcher.isExcludedIp("10.0.0.1", List.of("10.0.0.0/8"))).isTrue();
    }

    private BlocklistRule rule(BlocklistMatchType type, String value) {
        return new BlocklistRule(null, type, value, null, true, "test");
    }

    private java.util.Optional<BlocklistRule> match(List<BlocklistRule> rules, String ip, String ua, String path) {
        return matcher.match(new BlocklistRequest(ip, ua, path), rules, now);
    }
}

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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * IP 字面量与 CIDR 匹配用例。
 *
 * @author wenbin
 * @since 2026-11-04
 */
class IpAddressTest {

    @Test
    @DisplayName("精确 IP 仅匹配同一地址，不做 DNS 解析")
    void exactMatch() {
        assertThat(IpAddress.matches("10.0.0.1", "10.0.0.1")).isTrue();
        assertThat(IpAddress.matches("10.0.0.1", "10.0.0.2")).isFalse();
        assertThat(IpAddress.matches("example.com", "10.0.0.1")).isFalse();
    }

    @Test
    @DisplayName("CIDR 按前缀位匹配，并规范化网络地址")
    void cidrMatch() {
        assertThat(IpAddress.matchesCidr("10.0.0.0/8", "10.9.8.7")).isTrue();
        assertThat(IpAddress.matchesCidr("10.0.0.0/8", "11.0.0.1")).isFalse();
        assertThat(IpAddress.matchesCidr("192.168.1.128/25", "192.168.1.200")).isTrue();
        assertThat(IpAddress.matchesCidr("192.168.1.128/25", "192.168.1.1")).isFalse();
        assertThat(IpAddress.matchesCidr("0.0.0.0/0", "203.0.113.5")).isTrue();
    }

    @Test
    @DisplayName("IPv6 与 IPv4-mapped 形式可用")
    void ipv6Match() {
        assertThat(IpAddress.matches("::1", "[::1]")).isTrue();
        assertThat(IpAddress.matchesCidr("2001:db8::/32", "2001:db8::1")).isTrue();
        assertThat(IpAddress.matchesCidr("2001:db8::/32", "2001:db9::1")).isFalse();
    }

    @Test
    @DisplayName("非法 CIDR/越界前缀按不命中处理")
    void invalidInput() {
        assertThat(IpAddress.matchesCidr("10.0.0.0/33", "10.0.0.1")).isFalse();
        assertThat(IpAddress.matchesCidr("10.0.0.0/abc", "10.0.0.1")).isFalse();
        assertThat(IpAddress.matchesCidr("10.0.0.0", "10.0.0.1")).isFalse();
        assertThat(IpAddress.normalize("not-an-ip")).isNull();
    }

    @Test
    @DisplayName("非字面量一律非法，绝不触发域名解析")
    void rejectsNonLiterals() {
        assertThat(IpAddress.isValidIp("999.999.999.999")).isFalse();
        assertThat(IpAddress.isValidIp("1.2.3.4.5")).isFalse();
        assertThat(IpAddress.isValidIp("deadbeef")).isFalse();
        assertThat(IpAddress.isValidIp("face.de")).isFalse();
        assertThat(IpAddress.isValidIp("beef.be")).isFalse();
        assertThat(IpAddress.isValidIp("1.2.3")).isFalse();
        assertThat(IpAddress.isValidIp("1.2.3.256")).isFalse();
    }

    @Test
    @DisplayName("IPv6 省略、嵌入 IPv4、方括号带端口均按字面量解析")
    void ipv6Literals() {
        assertThat(IpAddress.isValidIp("[2001:db8::1]:443")).isTrue();
        assertThat(IpAddress.isValidIp("::ffff:1.2.3.4")).isTrue();
        assertThat(IpAddress.isValidIp("1:2:3:4:5:6:7:8")).isTrue();
        assertThat(IpAddress.isValidIp("1:2:3:4:5:6:7")).isFalse();
        assertThat(IpAddress.isValidIp("1::2::3")).isFalse();
        assertThat(IpAddress.isValidIp("12345::1")).isFalse();
    }
}

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
package cn.ypbin.starter.sign.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link IpWhitelist} 纯函数测试（含边界与 fail-closed 对抗用例）。
 *
 * @author wenbin
 * @since 2026-10-05
 */
class IpWhitelistTest {

    // ---------- 空配置 = 不限来源（既有语义） ----------

    @Test
    void shouldAllowAnyWhenWhitelistAbsent() {
        assertThat(IpWhitelist.matches(null, "1.2.3.4")).isTrue();
        assertThat(IpWhitelist.matches("", "1.2.3.4")).isTrue();
        assertThat(IpWhitelist.matches("   ", "1.2.3.4")).isTrue();
    }

    @Test
    void shouldRejectWhenWhitelistConfiguredButIpMissing() {
        // 配了白名单却拿不到来源地址 ⇒ 无法判定 ⇒ 拒绝（fail-closed，不放行）
        assertThat(IpWhitelist.matches("10.0.0.0/8", null)).isFalse();
        assertThat(IpWhitelist.matches("10.0.0.0/8", "")).isFalse();
        assertThat(IpWhitelist.matches("10.0.0.0/8", "  ")).isFalse();
    }

    // ---------- 精确 IP ----------

    @Test
    void shouldMatchExactIpv4() {
        assertThat(IpWhitelist.matches("192.168.1.10", "192.168.1.10")).isTrue();
        assertThat(IpWhitelist.matches("192.168.1.10", "192.168.1.11")).isFalse();
    }

    @Test
    void shouldMatchExactIpv6() {
        assertThat(IpWhitelist.matches("2001:db8::1", "2001:db8::1")).isTrue();
        assertThat(IpWhitelist.matches("2001:db8::1", "2001:db8::2")).isFalse();
    }

    // ---------- CIDR ----------

    @Test
    void shouldMatchCidrRangeBoundaries() {
        // /24 的边界：网络地址与广播地址本身都算命中（按前缀匹配，不做保留地址排除）
        assertThat(IpWhitelist.matches("192.168.1.0/24", "192.168.1.0")).isTrue();
        assertThat(IpWhitelist.matches("192.168.1.0/24", "192.168.1.255")).isTrue();
        assertThat(IpWhitelist.matches("192.168.1.0/24", "192.168.1.128")).isTrue();
        // 相邻网段必须不命中
        assertThat(IpWhitelist.matches("192.168.1.0/24", "192.168.2.0")).isFalse();
        assertThat(IpWhitelist.matches("192.168.1.0/24", "192.168.0.255")).isFalse();
    }

    @Test
    void shouldMatchCidrNonByteAlignedMask() {
        // /25 是"非 8 位对齐"的掩码，验证位运算分支（不只整字节比较）
        assertThat(IpWhitelist.matches("192.168.1.0/25", "192.168.1.127")).isTrue();
        assertThat(IpWhitelist.matches("192.168.1.0/25", "192.168.1.128")).isFalse();
        // /31：两位主机位
        assertThat(IpWhitelist.matches("10.0.0.0/31", "10.0.0.1")).isTrue();
        assertThat(IpWhitelist.matches("10.0.0.0/31", "10.0.0.2")).isFalse();
        // /32 = 精确单机
        assertThat(IpWhitelist.matches("10.0.0.5/32", "10.0.0.5")).isTrue();
        assertThat(IpWhitelist.matches("10.0.0.5/32", "10.0.0.6")).isFalse();
    }

    @Test
    void shouldMatchIpv6Cidr() {
        assertThat(IpWhitelist.matches("2001:db8::/32", "2001:db8:1234::1")).isTrue();
        assertThat(IpWhitelist.matches("2001:db8::/32", "2001:db9::1")).isFalse();
    }

    // ---------- 地址族隔离（关键安全边界） ----------

    @Test
    void shouldNotMatchAcrossAddressFamilies() {
        // IPv4 白名单不得匹配 IPv6 地址，反之亦然：否则出现"族混淆"绕过
        assertThat(IpWhitelist.matches("0.0.0.0/0", "2001:db8::1")).isFalse();
        assertThat(IpWhitelist.matches("::/0", "1.2.3.4")).isFalse();
    }

    @Test
    void shouldNormalizeIpv4MappedIpv6() {
        // ::ffff:192.168.1.10 应归一到 IPv4 语义后与 IPv4 白名单匹配
        assertThat(IpWhitelist.matches("192.168.1.10", "::ffff:192.168.1.10")).isTrue();
        assertThat(IpWhitelist.matches("192.168.1.0/24", "::ffff:192.168.1.10")).isTrue();
        assertThat(IpWhitelist.matches("192.168.1.0/24", "::ffff:10.0.0.1")).isFalse();
    }

    // ---------- 多条目 / 空白容错 ----------

    @Test
    void shouldSupportMultipleEntriesWithWhitespace() {
        String whitelist = " 10.0.0.0/8 , 192.168.1.10 ,, 2001:db8::/32 ";
        assertThat(IpWhitelist.matches(whitelist, "10.5.5.5")).isTrue();
        assertThat(IpWhitelist.matches(whitelist, "192.168.1.10")).isTrue();
        assertThat(IpWhitelist.matches(whitelist, "2001:db8::9")).isTrue();
        assertThat(IpWhitelist.matches(whitelist, "8.8.8.8")).isFalse();
    }

    // ---------- fail-closed：非法条目不得放行 ----------

    @Test
    void shouldRejectWhenOnlyIllegalEntriesConfigured() {
        // 白名单条目全非法 ⇒ 不匹配任何地址（"看不懂规则"绝不等于放行）
        assertThat(IpWhitelist.matches("not-an-ip", "1.2.3.4")).isFalse();
        assertThat(IpWhitelist.matches("example.com", "1.2.3.4")).isFalse();
        assertThat(IpWhitelist.matches("1.2.3.4/abc", "1.2.3.4")).isFalse();
        assertThat(IpWhitelist.matches("1.2.3.4/99", "1.2.3.4")).isFalse();
        assertThat(IpWhitelist.matches("1.2.3.4/-1", "1.2.3.4")).isFalse();
    }

    @Test
    void shouldRejectUnparseableClientIp() {
        assertThat(IpWhitelist.matches("10.0.0.0/8", "not-an-ip")).isFalse();
        // 域名不做 DNS 解析（既是性能坑也是 SSRF 面）⇒ 无法作为来源地址匹配
        assertThat(IpWhitelist.matches("10.0.0.0/8", "evil.example.com")).isFalse();
    }

    @Test
    void shouldNotResolveHostnameInWhitelist() {
        // 白名单写 localhost：isLiteralAddress 应拒绝（不触发 DNS 解析）
        assertThat(IpWhitelist.matches("localhost", "127.0.0.1")).isFalse();
    }

    @Test
    void shouldRejectIllegalEntryButStillHonorLegalOnes() {
        // 混合：一条非法 + 一条合法 ⇒ 合法那条仍生效（非法条目不参与匹配，但不影响整体）
        String whitelist = "bogus, 10.0.0.0/8";
        assertThat(IpWhitelist.matches(whitelist, "10.1.1.1")).isTrue();
        assertThat(IpWhitelist.matches(whitelist, "8.8.8.8")).isFalse();
    }

    // ---------- invalidEntries：让"配了不生效"可查 ----------

    @Test
    void shouldReportInvalidEntries() {
        assertThat(IpWhitelist.invalidEntries("10.0.0.0/8, bogus, 1.2.3.4/99, 1.2.3.4"))
            .containsExactly("bogus", "1.2.3.4/99");
    }

    @Test
    void shouldReturnEmptyInvalidEntriesForNullOrBlank() {
        assertThat(IpWhitelist.invalidEntries(null)).isEmpty();
        assertThat(IpWhitelist.invalidEntries("")).isEmpty();
        assertThat(IpWhitelist.invalidEntries("  ")).isEmpty();
    }

    @Test
    void shouldReturnEmptyInvalidEntriesWhenAllLegal() {
        assertThat(IpWhitelist.invalidEntries("10.0.0.0/8, 192.168.1.10, 2001:db8::/32")).isEmpty();
        assertThat(IpWhitelist.invalidEntries("10.0.0.0/8,,  ")).isEmpty();
    }

    // ---------- isValidEntry ----------

    @Test
    void shouldValidateEntrySyntax() {
        assertThat(IpWhitelist.isValidEntry("10.0.0.0/8")).isTrue();
        assertThat(IpWhitelist.isValidEntry("192.168.1.10")).isTrue();
        assertThat(IpWhitelist.isValidEntry("2001:db8::/32")).isTrue();
        assertThat(IpWhitelist.isValidEntry("::1")).isTrue();
        assertThat(IpWhitelist.isValidEntry("10.0.0.0/33")).isFalse();
        assertThat(IpWhitelist.isValidEntry("2001:db8::/129")).isFalse();
        assertThat(IpWhitelist.isValidEntry("")).isFalse();
        assertThat(IpWhitelist.isValidEntry(null)).isFalse();
    }

    // ---------- 端口 / 方括号形态 ----------

    @Test
    void shouldHandleBracketedIpv6() {
        assertThat(IpWhitelist.matches("[::1]", "::1")).isTrue();
        assertThat(IpWhitelist.matches("[2001:db8::/32]", "2001:db8::5")).isTrue();
    }

    @Test
    void shouldStripIpv4PortSuffix() {
        // 带端口的 IPv4（单冒号 + 含点）应剥离端口后匹配
        assertThat(IpWhitelist.matches("192.168.1.10", "192.168.1.10:5678")).isTrue();
    }

    // ---------- 纯函数纪律 ----------

    @Test
    void shouldNotThrowOnAnyInput() {
        assertThatCode(() -> {
            IpWhitelist.matches("bad", "worse");
            IpWhitelist.matches("/", "/");
            IpWhitelist.matches("///", "//");
            IpWhitelist.invalidEntries("/");
        }).doesNotThrowAnyException();
    }

    @Test
    void shouldHandleSlashEdgeCases() {
        assertThat(IpWhitelist.matches("/", "1.2.3.4")).isFalse();
        assertThat(IpWhitelist.matches("10.0.0.0/", "10.0.0.1")).isFalse();
        assertThat(IpWhitelist.invalidEntries("/")).containsExactly("/");
    }

    @Test
    void shouldKeepInvalidEntryOrder() {
        List<String> invalid = IpWhitelist.invalidEntries("zzz, 10.0.0.0/8, aaa, bbb");
        assertThat(invalid).containsExactly("zzz", "aaa", "bbb");
    }
}

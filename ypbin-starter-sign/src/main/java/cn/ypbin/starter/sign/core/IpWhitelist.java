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

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.jspecify.annotations.Nullable;

/**
 * IP 白名单匹配（纯函数，零 IO，可穷举单测）。
 *
 * <p>支持两种条目：</p>
 * <ul>
 *     <li><b>精确 IP</b>：{@code 192.168.1.10}（IPv4）或 {@code 2001:db8::1}（IPv6）；</li>
 *     <li><b>CIDR 网段</b>：{@code 192.168.1.0/24}、{@code 10.0.0.0/8}、{@code 2001:db8::/32}。</li>
 * </ul>
 *
 * <p><b>fail-closed 纪律</b>：白名单条目本身非法（无法解析的 IP / 越界的掩码长度）时，
 * 该条目<b>不匹配任何地址</b>并返回 {@code false}——绝不因"看不懂这条规则"而放行。
 * 调用方应通过 {@link #invalidEntries(String)} 把非法条目暴露出来记日志，避免静默失效。</p>
 *
 * <p><b>IPv4-mapped IPv6</b>：{@code ::ffff:192.168.1.10} 会归一到 IPv4 语义后再比较，
 * 避免"白名单写了 IPv4、请求经 IPv6 栈进来"而误拒或误放。</p>
 *
 * @author wenbin
 * @since 2026-10-05
 */
public final class IpWhitelist {

    /** 单个 IPv4 地址的位数。 */
    private static final int IPV4_BITS = 32;

    /** 单个 IPv6 地址的位数。 */
    private static final int IPV6_BITS = 128;

    /** IPv4 与 IPv6 的字节长度。 */
    private static final int IPV4_BYTES = 4;

    private static final int IPV6_BYTES = 16;

    private IpWhitelist() {
    }

    /**
     * 判定地址是否命中白名单。
     *
     * @param whitelist 逗号分隔的白名单（可为 {@code null}/空白 ⇒ 此时返回 {@code true}，表示"不限来源"）
     * @param ip        待判地址（可为 {@code null}/空白 ⇒ 返回 {@code false}，无法判定即拒绝）
     * @return 命中返回 {@code true}
     */
    public static boolean matches(@Nullable String whitelist, @Nullable String ip) {
        if (whitelist == null || whitelist.isBlank()) {
            // 未配置 = 不限来源（与既有字段语义一致：空 = 不限）
            return true;
        }
        if (ip == null || ip.isBlank()) {
            // 配了白名单却拿不到来源地址 ⇒ 无法判定 ⇒ 拒绝（fail-closed）
            return false;
        }
        InetAddress address = parseAddress(ip.trim());
        if (address == null) {
            return false;
        }
        byte[] target = normalize(address);
        for (String raw : whitelist.split(",")) {
            String entry = normalizeBrackets(raw.trim());
            if (entry.isEmpty()) {
                continue;
            }
            if (matchesEntry(entry, target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 挑出白名单中**非法**的条目（供调用方记日志，避免静默失效）。
     *
     * @param whitelist 逗号分隔的白名单（可为 {@code null}）
     * @return 非法条目列表（**绝不返回 {@code null}**；保持首次出现顺序）
     */
    public static java.util.List<String> invalidEntries(@Nullable String whitelist) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (whitelist == null || whitelist.isBlank()) {
            return out;
        }
        for (String raw : whitelist.split(",")) {
            String entry = normalizeBrackets(raw.trim());
            if (!entry.isEmpty() && !isValidEntry(entry)) {
                out.add(entry);
            }
        }
        return out;
    }

    /**
     * 判定单个条目是否合法（供 {@link #invalidEntries(String)} 与调用方复用）。
     *
     * @param entry 单条目（已 trim）
     * @return 合法返回 {@code true}
     */
    public static boolean isValidEntry(String entry) {
        if (entry == null || entry.isBlank()) {
            return false;
        }
        entry = normalizeBrackets(entry.trim());
        int slash = entry.indexOf('/');
        if (slash < 0) {
            return parseAddress(entry) != null;
        }
        String network = entry.substring(0, slash);
        String maskText = entry.substring(slash + 1);
        InetAddress address = parseAddress(network);
        if (address == null) {
            return false;
        }
        Integer mask = parseMask(maskText);
        if (mask == null) {
            return false;
        }
        return mask <= bitLength(normalize(address));
    }

    private static boolean matchesEntry(String entry, byte[] target) {
        int slash = entry.indexOf('/');
        if (slash < 0) {
            InetAddress exact = parseAddress(entry);
            if (exact == null) {
                // 非法条目：不匹配任何地址（fail-closed）
                return false;
            }
            return java.util.Arrays.equals(normalize(exact), target);
        }
        InetAddress networkAddress = parseAddress(entry.substring(0, slash));
        Integer mask = parseMask(entry.substring(slash + 1));
        if (networkAddress == null || mask == null) {
            return false;
        }
        byte[] network = normalize(networkAddress);
        // 地址族必须一致：IPv4 条目不能匹配 IPv6 地址（反之亦然）
        if (network.length != target.length) {            return false;
        }
        if (mask > bitLength(network)) {
            return false;
        }
        return prefixEquals(network, target, mask);
    }

    /**
     * 按前 {@code bits} 位比较两个等长地址。
     */
    private static boolean prefixEquals(byte[] left, byte[] right, int bits) {
        int fullBytes = bits / 8;
        for (int i = 0; i < fullBytes; i++) {
            if (left[i] != right[i]) {
                return false;
            }
        }
        int remaining = bits % 8;
        if (remaining == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remaining) & 0xFF;
        return (left[fullBytes] & mask) == (right[fullBytes] & mask);
    }

    private static int bitLength(byte[] address) {
        return address.length == IPV4_BYTES ? IPV4_BITS : IPV6_BITS;
    }

    @Nullable
    private static Integer parseMask(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            int mask = Integer.parseInt(text.trim());
            return (mask < 0) ? null : mask;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 去掉 IPv6 方括号（如 {@code [::1]} / {@code [2001:db8::/32]}）。
     *
     * <p>必须在<b>按 {@code /} 切分之前</b>调用：否则 {@code [2001:db8::/32]} 会被切成
     * {@code [2001:db8::} 与 {@code 32]}，网络段带方括号而解析失败。这是单测抓出的真实缺陷。</p>
     */
    private static String normalizeBrackets(String value) {
        if (value != null && value.length() >= 2 && value.startsWith("[") && value.endsWith("]")) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    @Nullable
    private static InetAddress parseAddress(String text) {
        // 仅接受字面量地址（不做 DNS 解析）：白名单里的域名会在每次请求触发解析，既是性能坑也是 SSRF 面
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = normalizeBrackets(text.trim());
        // 去掉 IPv4 的端口后缀（如 1.2.3.4:5678）：仅当"恰有一个冒号且含点"才剥离，
        // 否则 IPv6 地址会被误截断。IPv6 带端口应写成 [::1]:8080（上面的方括号分支已处理）。
        int firstColon = value.indexOf(':');
        if (firstColon >= 0 && value.indexOf('.') >= 0 && value.indexOf(':', firstColon + 1) < 0) {
            value = value.substring(0, firstColon);
        }
        if (!isLiteralAddress(value)) {
            return null;
        }
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    /**
     * 判定是否字面量地址（拒绝域名，避免 DNS 解析）。
     */
    private static boolean isLiteralAddress(String value) {
        if (value.isEmpty()) {
            return false;
        }
        boolean hasColon = value.indexOf(':') >= 0;
        boolean hasDot = value.indexOf('.') >= 0;
        if (!hasColon && !hasDot) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean legal = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                || c == ':' || c == '.';
            if (!legal) {
                return false;
            }
        }
        return true;
    }

    /**
     * 归一化地址：把 IPv4-mapped IPv6（{@code ::ffff:a.b.c.d}）压回 4 字节 IPv4 语义。
     */
    private static byte[] normalize(InetAddress address) {
        byte[] raw = address.getAddress();
        if (raw.length == IPV6_BYTES && isIpv4Mapped(raw)) {
            byte[] v4 = new byte[IPV4_BYTES];
            System.arraycopy(raw, IPV6_BYTES - IPV4_BYTES, v4, 0, IPV4_BYTES);
            return v4;
        }
        return raw;
    }

    private static boolean isIpv4Mapped(byte[] raw) {
        for (int i = 0; i < 10; i++) {
            if (raw[i] != 0) {
                return false;
            }
        }
        return (raw[10] & 0xFF) == 0xFF && (raw[11] & 0xFF) == 0xFF;
    }
}

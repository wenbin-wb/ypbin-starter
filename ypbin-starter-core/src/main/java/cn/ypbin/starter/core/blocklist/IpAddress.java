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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * IP 字面量与 CIDR 网段匹配工具。
 *
 * <p>只接受 IP 字面量，<b>任何情况下都不做 DNS 解析</b>。IPv4 手工解析四段十进制；
 * IPv6 先校验「至多一个 {@code ::}、组数不超过 8、每组 1–4 位十六进制」，再转字节；
 * 非字面量（如 {@code face.de}、{@code deadbeef}、{@code 999.999.999.999}）一律判非法。
 * 这样放在请求链路上既不会被域名解析拖慢，也不会被用来探测内网或伪造客户端 IP。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
public final class IpAddress {

    private static final int IPV4_BITS = 32;

    private static final int IPV6_BITS = 128;

    private static final int IPV4_BYTES = 4;

    private static final int IPV6_BYTES = 16;

    private static final int IPV6_GROUPS = 8;

    private static final int MAX_GROUP_HEX_LENGTH = 4;

    private static final int MAX_OCTET = 255;

    /**
     * 判断 IP 是否命中「精确 IP 或 CIDR」规则。
     *
     * @param pattern 精确 IP 或 CIDR
     * @param candidate 待判断的 IP
     * @return 命中返回 {@code true}；任一参数非法返回 {@code false}
     */
    public static boolean matches(String pattern, String candidate) {
        String value = pattern.trim();
        if (value.isEmpty()) {
            return false;
        }
        int slash = value.indexOf('/');
        if (slash < 0) {
            IpAddress expected = parse(value);
            IpAddress actual = parse(candidate);
            return expected != null && actual != null
                && expected.bitLength == actual.bitLength
                && Arrays.equals(expected.bytes, actual.bytes);
        }
        return matchesCidr(value, candidate);
    }

    /**
     * 判断 IP 是否落在 CIDR 网段内。
     *
     * @param cidr CIDR，如 {@code 10.0.0.0/8}
     * @param candidate 待判断的 IP
     * @return 命中返回 {@code true}；CIDR 或 IP 非法返回 {@code false}
     */
    public static boolean matchesCidr(String cidr, String candidate) {
        CidrBlock block = CidrBlock.parse(cidr);
        IpAddress address = parse(candidate);
        if (block == null || address == null || block.bitLength() != address.bitLength) {
            return false;
        }
        if (block.prefixBits() == 0) {
            return true;
        }
        int fullBytes = block.prefixBits() / 8;
        int remainingBits = block.prefixBits() % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (block.bytes()[i] != address.bytes[i]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainingBits);
        return (block.bytes()[fullBytes] & mask) == (address.bytes[fullBytes] & mask);
    }

    /**
     * 判断是否为合法 IP 字面量。
     *
     * @param raw 原始文本
     * @return 合法返回 {@code true}
     */
    public static boolean isValidIp(@Nullable String raw) {
        return parse(raw) != null;
    }

    /**
     * 判断是否为合法 CIDR。
     *
     * @param raw 原始文本
     * @return 合法返回 {@code true}
     */
    public static boolean isValidCidr(@Nullable String raw) {
        return CidrBlock.parse(raw) != null;
    }

    /**
     * 规范化 IP 字面量（去掉 IPv6 方括号/zone、统一 IPv6 形式）。
     *
     * @param raw 原始文本
     * @return 规范化结果；非法时返回 {@code null}
     */
    @Nullable
    public static String normalize(@Nullable String raw) {
        IpAddress address = parse(raw);
        return address == null ? null : address.hostAddress();
    }

    /**
     * 解析 IP 字面量。
     *
     * @param raw 原始文本
     * @return 解析结果；非法时返回 {@code null}
     */
    @Nullable
    static IpAddress parse(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            if (end > 1) {
                value = value.substring(1, end);
            }
        }
        int zone = value.indexOf('%');
        if (zone >= 0) {
            value = value.substring(0, zone);
        }
        if (value.indexOf(':') >= 0) {
            IpAddress parsed = parseIpv6(value);
            return parsed == null ? null : parsed.foldV4Mapped();
        }
        return parseIpv4(value);
    }

    /**
     * 手工解析 IPv4，不经过 {@link InetAddress#getByName}，避免域名解析。
     */
    @Nullable
    private static IpAddress parseIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != IPV4_BYTES) {
            return null;
        }
        byte[] bytes = new byte[IPV4_BYTES];
        for (int i = 0; i < parts.length; i++) {
            Integer octet = parseDecimal(parts[i]);
            if (octet == null || octet > MAX_OCTET) {
                return null;
            }
            bytes[i] = octet.byteValue();
        }
        return new IpAddress(bytes);
    }

    /**
     * 手工解析 IPv6：支持 {@code ::} 省略、IPv4 嵌入形式与 zone 后缀（zone 已在入口剥离）。
     */
    @Nullable
    private static IpAddress parseIpv6(String value) {
        String normalized = replaceEmbeddedIpv4(value);
        if (normalized == null) {
            return null;
        }
        int doubleColon = normalized.indexOf("::");
        if (doubleColon >= 0 && doubleColon != normalized.lastIndexOf("::")) {
            return null;
        }
        String leftPart = doubleColon >= 0 ? normalized.substring(0, doubleColon) : normalized;
        String rightPart = doubleColon >= 0 ? normalized.substring(doubleColon + 2) : "";
        Optional<List<Integer>> left = parseGroups(leftPart);
        Optional<List<Integer>> right = parseGroups(rightPart);
        if (left.isEmpty() || right.isEmpty()) {
            return null;
        }
        int total = left.get().size() + right.get().size();
        if (doubleColon < 0 ? total != IPV6_GROUPS : total >= IPV6_GROUPS) {
            return null;
        }
        List<Integer> groups = new ArrayList<>(IPV6_GROUPS);
        groups.addAll(left.get());
        for (int i = total; i < IPV6_GROUPS; i++) {
            groups.add(0);
        }
        groups.addAll(right.get());
        byte[] bytes = new byte[IPV6_BYTES];
        for (int i = 0; i < groups.size(); i++) {
            int group = groups.get(i);
            bytes[i * 2] = (byte) (group >> 8);
            bytes[i * 2 + 1] = (byte) group;
        }
        return new IpAddress(bytes);
    }

    /**
     * 把 IPv6 尾部的 IPv4 嵌入形式替换成两个十六进制组；返回 {@code null} 表示嵌入地址非法。
     */
    @Nullable
    private static String replaceEmbeddedIpv4(String value) {
        int lastColon = value.lastIndexOf(':');
        if (lastColon < 0) {
            return null;
        }
        String tail = value.substring(lastColon + 1);
        if (tail.indexOf('.') < 0) {
            return value;
        }
        IpAddress ipv4 = parseIpv4(tail);
        if (ipv4 == null) {
            return null;
        }
        int high = ((ipv4.bytes[0] & 0xFF) << 8) | (ipv4.bytes[1] & 0xFF);
        int low = ((ipv4.bytes[2] & 0xFF) << 8) | (ipv4.bytes[3] & 0xFF);
        return value.substring(0, lastColon + 1) + Integer.toHexString(high) + ":"
            + Integer.toHexString(low);
    }

    /**
     * 解析冒号分隔的十六进制组；空段（由 {@code ::} 产生）按非法处理。
     */
    private static Optional<List<Integer>> parseGroups(String part) {
        if (part.isEmpty()) {
            return Optional.of(List.of());
        }
        String[] rawGroups = part.split(":", -1);
        List<Integer> groups = new ArrayList<>(rawGroups.length);
        for (String rawGroup : rawGroups) {
            if (rawGroup.isEmpty() || rawGroup.length() > MAX_GROUP_HEX_LENGTH) {
                return Optional.empty();
            }
            try {
                groups.add(Integer.parseInt(rawGroup, 16));
            } catch (NumberFormatException ex) {
                return Optional.empty();
            }
        }
        return Optional.of(groups);
    }

    /**
     * 解析十进制八位组，拒绝符号、空白与非数字。
     */
    @Nullable
    private static Integer parseDecimal(String value) {
        if (value.isEmpty() || value.length() > 3) {
            return null;
        }
        int number = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
            number = number * 10 + (c - '0');
        }
        return number;
    }

    private final byte[] bytes;

    private final int bitLength;

    private IpAddress(byte[] bytes) {
        this.bytes = bytes.clone();
        this.bitLength = bytes.length * 8;
    }

    /**
     * 把 IPv4-mapped IPv6（{@code ::ffff:a.b.c.d}）折叠为 4 字节，使精确匹配/CIDR 与 IPv4 一致；
     * IPv4-compatible（{@code ::a.b.c.d}，前 10 字节全 0 且第 11/12 字节非 0xFF）不折叠。
     */
    private IpAddress foldV4Mapped() {
        if (bytes.length != IPV6_BYTES) {
            return this;
        }
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return this;
            }
        }
        if (bytes[10] != (byte) 0xFF || bytes[11] != (byte) 0xFF) {
            return this;
        }
        return new IpAddress(new byte[] {bytes[12], bytes[13], bytes[14], bytes[15]});
    }

    /**
     * 使用 {@link InetAddress#getByAddress(byte[])} 做纯字节格式化，不触发名称解析。
     */
    private String hostAddress() {
        try {
            return InetAddress.getByAddress(bytes).getHostAddress().toLowerCase(Locale.ROOT);
        } catch (UnknownHostException ex) {
            // 构造时已保证长度为 4/16，正常路径不可达；返回空串让调用方按非法处理
            return "";
        }
    }

    /**
     * CIDR 解析结果。
     *
     * @param bytes      网络地址字节
     * @param prefixBits 前缀位数
     */
    private record CidrBlock(byte[] bytes, int prefixBits) {

        @Nullable
        static CidrBlock parse(@Nullable String cidr) {
            if (cidr == null) {
                return null;
            }
            String value = cidr.trim();
            int slash = value.indexOf('/');
            if (slash <= 0 || slash == value.length() - 1) {
                return null;
            }
            IpAddress address = IpAddress.parse(value.substring(0, slash));
            if (address == null) {
                return null;
            }
            int bits;
            try {
                bits = Integer.parseInt(value.substring(slash + 1).trim());
            } catch (NumberFormatException ex) {
                return null;
            }
            int maxBits = address.bitLength == IPV6_BITS ? IPV6_BITS : IPV4_BITS;
            if (bits < 0 || bits > maxBits) {
                return null;
            }
            byte[] network = address.bytes.clone();
            for (int i = 0; i < network.length; i++) {
                int bitOffset = i * 8;
                if (bitOffset >= bits) {
                    network[i] = 0;
                } else if (bitOffset + 8 > bits) {
                    int keep = bits - bitOffset;
                    network[i] = (byte) (network[i] & (0xFF << (8 - keep)));
                }
            }
            return new CidrBlock(network, bits);
        }

        int bitLength() {
            return bytes.length * 8;
        }
    }
}

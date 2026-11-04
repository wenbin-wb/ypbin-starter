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
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * IP 字面量与 CIDR 网段匹配工具。
 *
 * <p>只接受 IP 字面量，不做 DNS 解析：{@code InetAddress.getByName} 对主机名会发起解析，
 * 放在请求链路上既慢又可被用来探测内网。这里先按字面量格式校验，再交给 JDK 解析。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
public final class IpAddress {

    private static final Pattern IPV4_LITERAL = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private static final Pattern IPV6_LITERAL = Pattern.compile("^[0-9a-fA-F:.]+$");

    private static final int IPV4_BITS = 32;

    private static final int IPV6_BITS = 128;

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
     * 规范化 IP 字面量（去掉 IPv6 方括号/zone、统一 IPv4-mapped 形式）。
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
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        int zone = value.indexOf('%');
        if (zone >= 0) {
            value = value.substring(0, zone);
        }
        if (!IPV4_LITERAL.matcher(value).matches() && !IPV6_LITERAL.matcher(value).matches()) {
            return null;
        }
        try {
            return new IpAddress(InetAddress.getByName(value).getAddress());
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    private final byte[] bytes;

    private final int bitLength;

    private IpAddress(byte[] bytes) {
        this.bytes = bytes.clone();
        this.bitLength = bytes.length * 8;
    }

    private String hostAddress() {
        try {
            return InetAddress.getByAddress(bytes).getHostAddress().toLowerCase(Locale.ROOT);
        } catch (UnknownHostException ex) {
            // 构造时已保证长度合法，这里的异常在正常路径不可达；返回空串让调用方按非法处理
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

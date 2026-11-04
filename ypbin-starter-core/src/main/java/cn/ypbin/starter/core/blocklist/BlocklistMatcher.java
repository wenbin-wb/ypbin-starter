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

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 访问黑名单匹配器。
 *
 * <p>匹配是只读、无状态、可并发的。规则按传入顺序判定，第一条命中即返回，因此调用方应把
 * 更精确的规则排在前面。非法规则值（坏 CIDR、空值）按「不命中」处理，避免一条坏配置
 * 阻断全部请求。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
public class BlocklistMatcher {

    private static final String PATH_SEPARATOR = "/";

    /**
     * 在规则集合中查找第一条命中项。
     *
     * @param request 请求信息
     * @param rules   规则集合
     * @param now     当前时刻
     * @return 命中的规则；无命中返回空
     */
    public Optional<BlocklistRule> match(BlocklistRequest request, Collection<BlocklistRule> rules, Instant now) {
        String path = normalizePath(request.path());
        String userAgent = request.userAgent().toLowerCase(Locale.ROOT);
        for (BlocklistRule rule : rules) {
            if (rule == null || !rule.isEffective(now)) {
                continue;
            }
            String value = rule.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            if (matches(rule.getType(), value, request.clientIp(), userAgent, path)) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }

    /**
     * 判断路径是否在放行列表中。
     *
     * @param path     请求路径
     * @param patterns 放行路径模式（支持精确路径与 {@code *} / {@code /**} 后缀）
     * @return 命中返回 {@code true}
     */
    public boolean isExcludedPath(String path, List<String> patterns) {
        String normalized = normalizePath(path);
        for (String pattern : patterns) {
            if (pattern != null && matchesPath(normalized, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 IP 是否在放行列表中。
     *
     * @param clientIp 客户端 IP
     * @param patterns 精确 IP 或 CIDR
     * @return 命中返回 {@code true}
     */
    public boolean isExcludedIp(String clientIp, List<String> patterns) {
        for (String pattern : patterns) {
            if (pattern != null && IpAddress.matches(pattern, clientIp)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(BlocklistMatchType type, String value, String clientIp, String userAgent,
                                   String path) {
        return switch (type) {
            case IP -> IpAddress.matches(value, clientIp);
            case CIDR -> IpAddress.matchesCidr(value, clientIp);
            case USER_AGENT -> userAgent.contains(value.trim().toLowerCase(Locale.ROOT));
            case PATH -> matchesPath(path, value);
        };
    }

    /**
     * 路径前缀匹配。
     *
     * <p>先做归一化（编码分隔符、重复斜杠、大小写），再按配置的 {@code *} / {@code /**} 后缀
     * 决定是前缀还是精确匹配。安全过滤宁宽勿漏：{@code /System/Open-Api} 与
     * {@code /system/open-api} 视为同一路径。</p>
     */
    private static boolean matchesPath(String normalizedPath, String pattern) {
        String normalizedPattern = normalizePath(pattern);
        if (normalizedPattern.isEmpty()) {
            return false;
        }
        if (normalizedPattern.endsWith("/**")) {
            String prefix = normalizedPattern.substring(0, normalizedPattern.length() - 3);
            return normalizedPath.equals(prefix) || normalizedPath.startsWith(prefix + PATH_SEPARATOR);
        }
        if (normalizedPattern.endsWith("/*")) {
            String prefix = normalizedPattern.substring(0, normalizedPattern.length() - 2);
            return normalizedPath.equals(prefix) || normalizedPath.startsWith(prefix + PATH_SEPARATOR);
        }
        if (normalizedPattern.endsWith("*")) {
            return normalizedPath.startsWith(normalizedPattern.substring(0, normalizedPattern.length() - 1));
        }
        return normalizedPath.equals(normalizedPattern)
            || normalizedPath.startsWith(normalizedPattern + PATH_SEPARATOR);
    }

    /**
     * 归一化请求路径，降低编码/大小写/多余分隔符造成的绕过空间。
     *
     * @param raw 原始路径
     * @return 归一化路径（小写、前导斜杠、无重复斜杠）
     */
    static String normalizePath(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim().replace('\\', '/');
        value = value.replaceAll("(?i)%2f", "/").replaceAll("(?i)%5c", "/");
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        value = value.replaceAll("/{2,}", "/");
        if (value.isEmpty()) {
            return "/";
        }
        if (!value.startsWith(PATH_SEPARATOR)) {
            value = PATH_SEPARATOR + value;
        }
        return value.toLowerCase(Locale.ROOT);
    }
}

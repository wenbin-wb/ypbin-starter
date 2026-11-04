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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/**
 * 访问黑名单匹配器。
 *
 * <p>匹配是只读、无状态、可并发的。规则按传入顺序判定，第一条命中即返回，因此调用方应把
 * 更精确的规则排在前面。非法规则值（坏 CIDR、空值）按「不命中」处理，避免一条坏配置
 * 阻断全部请求。</p>
 *
 * <p><b>路径归一化</b>：最多连续解码 3 次（覆盖 {@code %252f} 这类双重编码）、消解 {@code .}
 * 与 {@code ..} 段、折叠重复斜杠并转小写；规则 pattern 归一化后进入有界缓存，热路径不再逐条
 * 编译正则。{@code /**} 匹配任意层级，{@code /*} 只匹配一层，单独的 {@code *} 匹配全部路径。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
public class BlocklistMatcher {

    private static final String PATH_SEPARATOR = "/";

    private static final Pattern MULTI_SLASH = Pattern.compile("/{2,}");

    private static final int MAX_DECODE_PASSES = 3;

    private static final int MAX_PATTERN_CACHE_SIZE = 2048;

    private final ConcurrentMap<String, String> patternCache = new ConcurrentHashMap<>();

    /**
     * 在规则集合中查找第一条命中项。
     *
     * @param request 请求信息
     * @param rules   规则集合
     * @param now     当前时刻
     * @return 命中的规则；无命中返回空
     */
    public Optional<BlocklistRule> match(BlocklistRequest request, Collection<BlocklistRule> rules, Instant now) {
        if (request == null || rules == null || rules.isEmpty()) {
            return Optional.empty();
        }
        String path = normalizePath(request.path());
        String userAgent = request.userAgent() == null
            ? "" : request.userAgent().toLowerCase(Locale.ROOT);
        for (BlocklistRule rule : rules) {
            if (rule == null || rule.getType() == null || !rule.isEffective(now)) {
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
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
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
        if (patterns == null || patterns.isEmpty() || clientIp == null) {
            return false;
        }
        for (String pattern : patterns) {
            if (pattern != null && IpAddress.matches(pattern, clientIp)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(BlocklistMatchType type, String value, String clientIp, String userAgent,
                            String path) {
        return switch (type) {
            case IP -> IpAddress.matches(value, clientIp);
            case CIDR -> IpAddress.matchesCidr(value, clientIp);
            case USER_AGENT -> userAgent.contains(value.trim().toLowerCase(Locale.ROOT));
            case PATH -> matchesPath(path, value);
        };
    }

    /**
     * 路径模式匹配：{@code /**} 任意层级，{@code /*} 一层，{@code *} 全部，其余为精确/前缀。
     */
    boolean matchesPath(String normalizedPath, String pattern) {
        if (pattern != null && "*".equals(pattern.trim())) {
            return true;
        }
        String normalizedPattern = normalizePattern(pattern);
        if (normalizedPattern.isEmpty()) {
            return false;
        }
        if (normalizedPattern.endsWith("/**")) {
            String prefix = normalizedPattern.substring(0, normalizedPattern.length() - 3);
            return normalizedPath.equals(prefix) || normalizedPath.startsWith(prefix + PATH_SEPARATOR);
        }
        if (normalizedPattern.endsWith("/*")) {
            String prefix = normalizedPattern.substring(0, normalizedPattern.length() - 2);
            if (normalizedPath.equals(prefix)) {
                return true;
            }
            if (!normalizedPath.startsWith(prefix + PATH_SEPARATOR)) {
                return false;
            }
            return normalizedPath.indexOf(PATH_SEPARATOR, prefix.length() + 1) < 0;
        }
        if (normalizedPattern.endsWith("*")) {
            return normalizedPath.startsWith(normalizedPattern.substring(0, normalizedPattern.length() - 1));
        }
        return normalizedPath.equals(normalizedPattern)
            || normalizedPath.startsWith(normalizedPattern + PATH_SEPARATOR);
    }

    private String normalizePattern(String pattern) {
        if (pattern == null) {
            return "";
        }
        if (patternCache.size() > MAX_PATTERN_CACHE_SIZE) {
            patternCache.clear();
        }
        return patternCache.computeIfAbsent(pattern, BlocklistMatcher::normalizePath);
    }

    /**
     * 归一化请求路径，降低编码/大小写/多余分隔符造成的绕过空间。
     *
     * @param raw 原始路径
     * @return 归一化路径（小写、前导斜杠、无重复斜杠、已消解点段）
     */
    static String normalizePath(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        for (int i = 0; i < MAX_DECODE_PASSES; i++) {
            String decoded = decode(value);
            if (decoded.equals(value)) {
                break;
            }
            value = decoded;
        }
        value = value.replace('\\', '/');
        value = MULTI_SLASH.matcher(value).replaceAll(PATH_SEPARATOR);
        value = removeDotSegments(value);
        if (value.isEmpty()) {
            return PATH_SEPARATOR;
        }
        if (!value.startsWith(PATH_SEPARATOR)) {
            value = PATH_SEPARATOR + value;
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static String decode(String value) {
        try {
            // '+' 在路径里是字面量而不是空格：先转义再交给 URLDecoder，避免把合法路径改坏
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            // 非法百分号编码按原样处理，后续仍会做分隔符与点段归一化
            return value;
        }
    }

    private static String removeDotSegments(String value) {
        String[] segments = value.split(PATH_SEPARATOR, -1);
        Deque<String> stack = new ArrayDeque<>();
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (!stack.isEmpty()) {
                    stack.removeLast();
                }
                continue;
            }
            stack.addLast(segment);
        }
        return PATH_SEPARATOR + String.join(PATH_SEPARATOR, stack);
    }
}

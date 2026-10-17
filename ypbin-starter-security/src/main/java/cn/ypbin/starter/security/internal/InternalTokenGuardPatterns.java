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
package cn.ypbin.starter.security.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * 内部凭证守卫的路径模式规范化。
 *
 * <p><strong>为什么必须有这一层</strong>：RAW_401 语义由 Servlet
 * {@code FilterRegistrationBean} 承载，而 Servlet 规范（12.2）只承认两种含通配的映射——
 * 以 {@code /*} 结尾的路径映射与 {@code *.ext} 扩展名映射。接入方按 Spring 习惯写 Ant 风格
 * {@code /internal/mqtt/**} 时，容器会把整个字符串当作<strong>精确字面量</strong>匹配 ⇒ 真实请求
 * {@code /internal/mqtt/webhook} 完全跳过过滤器，而"守卫已启用"的启动日志照常打印——这是最危险的
 * 静默失效形态（EMQX 侧把 HTTP 200 判为投递成功，报文既不重试也不落 {@code dropped.*}）。</p>
 *
 * <p>本类因此做两件事：</p>
 * <ol>
 *     <li>把尾部 {@code /**} 归一化为 Servlet 的 {@code /*}（兼容 Spring 习惯，不要求接入方记住
 *     Servlet 规范细节）；</li>
 *     <li>对其余含通配、却无法被容器安全解释的写法（通配符出现在路径中间，而非末尾或扩展名前缀）
 *     <strong>启动即失败</strong>，而不是让它静默退化成字面量匹配。</li>
 * </ol>
 *
 * <p><strong>不做 Ant 扩宽</strong>：{@link #toServletPatterns(List)} 的结果会原样用作 Sa-Token 放行
 * 清单（Servlet 的 {@code /*}、{@code *.ext} 与精确路径都是合法 Ant 模式，且语义不宽于 Servlet 口径）；
 * 绝不能在放行侧把 {@code /internal/mqtt/*} 放宽成 {@code /internal/mqtt/**}——守卫的过滤器只覆盖
 * 一层，放行更宽就会留下"既无登录校验、又无内部凭证校验"的裸路径。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
final class InternalTokenGuardPatterns {

    /** Ant 风格「任意层级」后缀 */
    private static final String ANT_ANY_SUFFIX = "/**";

    /** Servlet 规范的路径映射后缀 */
    private static final String SERVLET_PATH_MAPPING_SUFFIX = "/*";

    /** Servlet 规范的扩展名映射前缀 */
    private static final String SERVLET_EXTENSION_PREFIX = "*.";

    private InternalTokenGuardPatterns() {
    }

    /**
     * 把配置的 RAW_401 路径归一化为 Servlet 容器可正确解释的映射模式。
     *
     * @param patterns 配置的路径清单（允许含空白项，将被忽略）
     * @return Servlet 风格路径模式清单（顺序保持、去重前的原样长度）
     * @throws IllegalArgumentException 存在含通配但无法被 Servlet 规则解释的写法（fail-fast，
     *                                  绝不允许静默退化为字面量匹配）
     */
    static List<String> toServletPatterns(List<String> patterns) {
        List<String> result = new ArrayList<>(patterns.size());
        for (String raw : patterns) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String pattern = raw.trim();
            if (pattern.endsWith(ANT_ANY_SUFFIX)) {
                // /internal/mqtt/** ⇒ /internal/mqtt/*；/internal/** ⇒ /internal/*
                pattern = pattern.substring(0, pattern.length() - ANT_ANY_SUFFIX.length())
                    + SERVLET_PATH_MAPPING_SUFFIX;
            }
            validateServletPattern(pattern);
            result.add(pattern);
        }
        return result;
    }

    /**
     * 校验归一化后的模式能否被 Servlet 规则解释。
     *
     * <p>合法形态只有三类：空串、以 {@code /} 开头的路径映射（{@code /*} 结尾）或精确路径、
     * 以及纯 {@code *.ext} 扩展名映射。其余一律拒绝——<strong>包括看起来"无害"的漏写前导斜杠</strong>
     * （{@code internal/*}）与混合形态（{@code *.json/*}）：容器对它们的处理是「逐字面量匹配路径」，
     * 真实请求永远不会命中，于是又变成"启动日志照打、过滤器静默失效"，正是本类要消灭的形态。</p>
     *
     * @param pattern 归一化后的模式
     * @throws IllegalArgumentException 无法被 Servlet 规则解释
     */
    private static void validateServletPattern(String pattern) {
        if (pattern.isEmpty()) {
            // 空串 = 容器的 context root 映射；本守卫清单里不会出现（调用方已过滤空白项）
            return;
        }
        if (pattern.startsWith(SERVLET_EXTENSION_PREFIX)) {
            // *.ext：扩展名映射不允许再出现 '/'（'*.json/*' 这类混合形态容器永不匹配）
            if (pattern.indexOf('/') >= 0) {
                throw illegal(pattern, "扩展名映射 '*.ext' 中不得再出现 '/'");
            }
            return;
        }
        if (!pattern.startsWith("/")) {
            throw illegal(pattern, "路径必须以 '/' 开头（漏写前导斜杠时容器按字面量匹配，过滤器永不触发）");
        }
        if (!pattern.contains("*")) {
            // 精确路径：完全合法
            return;
        }
        if (pattern.endsWith(SERVLET_PATH_MAPPING_SUFFIX)) {
            String prefix = pattern.substring(0, pattern.length() - SERVLET_PATH_MAPPING_SUFFIX.length());
            if (!prefix.contains("*")) {
                return;
            }
            throw illegal(pattern, "路径映射只允许末尾一个 '*'，中间不得再出现通配符");
        }
        throw illegal(pattern, "含通配符但不是 '/*' 结尾的路径映射");
    }

    private static IllegalArgumentException illegal(String pattern, String reason) {
        return new IllegalArgumentException("[ypbin-starter] " + InternalTokenProperties.PREFIX
            + ".raw401-path-patterns 取值非法：path=" + pattern + "（" + reason + "）。"
            + "Servlet 规范（12.2）只支持以 '/' 开头的 '/*' 路径映射、'*.ext' 扩展名映射或精确路径"
            + "（Ant 风格的 '/xxx/**' 会被自动归一化为 '/xxx/*'）；"
            + "其余写法会被容器当作精确字面量匹配，导致凭证守卫静默失效。");
    }
}

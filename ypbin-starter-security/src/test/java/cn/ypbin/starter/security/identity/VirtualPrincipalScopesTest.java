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
package cn.ypbin.starter.security.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link VirtualPrincipalScopes} 用例。
 *
 * <p>重点锁死<b>安全底线</b>：一切含 {@code *} 的 scope（含 {@code iot:*}、{@code iot:device:*}、
 * {@code *:*} 等 pattern 形态）必须被剥离 —— Sa-Token 会把它们当 pattern 模糊匹配，
 * 漏掉即「作用域隔离形同虚设」（实测：{@code iot:*} 能命中 {@code iot:debug:send}）。</p>
 *
 * @author wenbin
 * @since 2026-09-30
 */
class VirtualPrincipalScopesTest {

    @Test
    @DisplayName("虚拟段：真实 ID / 哨兵小负数不走虚拟分支；保留段与 Long.MIN_VALUE 是虚拟")
    void virtualSegmentBoundaries() {
        // 真实用户（正数雪花 ID）与 Sa-Token 哨兵附近的小负数：均非虚拟
        assertThat(VirtualPrincipalScopes.isVirtualPrincipal(1L)).isFalse();
        assertThat(VirtualPrincipalScopes.isVirtualPrincipal(991_736_107L)).isFalse();
        assertThat(VirtualPrincipalScopes.isVirtualPrincipal(0L)).isFalse();
        assertThat(VirtualPrincipalScopes.isVirtualPrincipal(-1L)).isFalse();
        assertThat(VirtualPrincipalScopes.isVirtualPrincipal(-5L)).isFalse();
        assertThat(VirtualPrincipalScopes.isVirtualPrincipal(-999_999_999L)).isFalse();
        // 保留段
        assertThat(VirtualPrincipalScopes.isVirtualPrincipal(VirtualPrincipalScopes.DEFAULT_VIRTUAL_USER_ID_MAX))
            .isTrue();
        assertThat(VirtualPrincipalScopes.isVirtualPrincipal(-1_000_000_001L)).isTrue();
        assertThat(VirtualPrincipalScopes.isVirtualPrincipal(Long.MIN_VALUE)).isTrue();
        assertThat(VirtualPrincipalScopes.isVirtualPrincipal(null)).isFalse();
    }

    @Test
    @DisplayName("🔴 一切含 * 的 scope 被剥离（含 iot:*、*:*、iot:device:* 等 pattern 形态）")
    void allWildcardPatternsMustBeStripped() {
        for (String pattern : List.of("*", "*:*:*", "*:*", "iot:*", "iot:device:*",
            "iot:*:list", "*:device:list", "a:b:c:d:*")) {
            assertThat(VirtualPrincipalScopes.scopesToPermissionCodes(
                new LinkedHashSet<>(List.of(pattern))))
                .as("pattern %s 被放行 => 可越权命中未授予的权限码", pattern)
                .isEmpty();
        }
    }

    @Test
    @DisplayName("精确的非通配 scope 原样保留（业务侧之上再过滤白名单）")
    void exactCodesKept() {
        Set<String> scopes = new LinkedHashSet<>(List.of("iot:device:list", "iot:series:get"));

        assertThat(VirtualPrincipalScopes.scopesToPermissionCodes(scopes))
            .containsExactly("iot:device:list", "iot:series:get");
    }

    @Test
    @DisplayName("trim / 去空 / 去重，保持首次出现顺序")
    void normalizeAndDedup() {
        Set<String> scopes = new LinkedHashSet<>(
            List.of(" iot:device:list ", "iot:device:list", "  ", "iot:series:get"));

        assertThat(VirtualPrincipalScopes.scopesToPermissionCodes(scopes))
            .containsExactly("iot:device:list", "iot:series:get");
    }

    @Test
    @DisplayName("空 / null / 纯空白 => 空列表（绝不 null）")
    void emptyInputsYieldEmptyList() {
        assertThat(VirtualPrincipalScopes.scopesToPermissionCodes(null)).isNotNull().isEmpty();
        assertThat(VirtualPrincipalScopes.scopesToPermissionCodes(Set.of())).isNotNull().isEmpty();
        assertThat(VirtualPrincipalScopes.scopesToPermissionCodes(
            new LinkedHashSet<>(List.of("   ")))).isEmpty();
    }

    @Test
    @DisplayName("strippedScopes 能把被剥离的 pattern 报出来（供日志，不静默）")
    void strippedScopesMustReport() {
        Set<String> scopes = new LinkedHashSet<>(List.of("iot:device:list", "iot:*", "*:*:*"));

        assertThat(VirtualPrincipalScopes.strippedScopes(scopes)).containsExactly("iot:*", "*:*:*");
        assertThat(VirtualPrincipalScopes.strippedScopes(Set.of("iot:device:list"))).isEmpty();
        assertThat(VirtualPrincipalScopes.strippedScopes(null)).isEmpty();
        assertThat(VirtualPrincipalScopes.strippedScopes(new LinkedHashSet<>(List.of("  ")))).isEmpty();
    }

    @Test
    @DisplayName("containsWildcard / containsAnyWildcard")
    void wildcardDetection() {
        assertThat(VirtualPrincipalScopes.containsWildcard("iot:*")).isTrue();
        assertThat(VirtualPrincipalScopes.containsWildcard("iot:device:list")).isFalse();
        assertThat(VirtualPrincipalScopes.containsWildcard(null)).isFalse();
        assertThat(VirtualPrincipalScopes.containsAnyWildcard(List.of("iot:device:list", "iot:*"))).isTrue();
        assertThat(VirtualPrincipalScopes.containsAnyWildcard(List.of("iot:device:list"))).isFalse();
    }

    @Test
    @DisplayName("常量与 starter 既有约定同值（改了会被静默放大成超管）")
    void constantsMatchConvention() {
        assertThat(VirtualPrincipalScopes.SUPER_ADMIN).isEqualTo("*:*:*");
        assertThat(VirtualPrincipalScopes.ANY_WILDCARD).isEqualTo("*");
    }
}

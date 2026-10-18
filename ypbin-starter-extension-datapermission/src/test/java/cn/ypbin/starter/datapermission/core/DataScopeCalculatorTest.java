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
package cn.ypbin.starter.datapermission.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.datapermission.core.DataScopeCalculator.ResolvedScope;
import cn.ypbin.starter.datapermission.core.DataScopeCalculator.RoleScope;
import cn.ypbin.starter.datapermission.core.DataScopeCalculator.ScopeType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link DataScopeCalculator} 单元测试。
 *
 * <p>逐条覆盖 issue #107 的验收标准 (a)~(h)：单角色全部、多角色并集、本部门及以下、自定义（含后代）、
 * 未知/脏 code 不贡献、无角色拒绝全部、空 deptId 的读/写语义、部门数据成环。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class DataScopeCalculatorTest {

    /** 测试用部门树：1 ─ 2 ─ 4、1 ─ 3；5 是独立的另一棵树。 */
    private static Map<Long, List<Long>> deptTree() {
        Map<Long, List<Long>> children = new HashMap<>();
        children.put(1L, new ArrayList<>(List.of(2L, 3L)));
        children.put(2L, new ArrayList<>(List.of(4L)));
        children.put(3L, new ArrayList<>());
        children.put(4L, new ArrayList<>());
        children.put(5L, new ArrayList<>());
        return children;
    }

    private static RoleScope role(ScopeType type) {
        return new RoleScope(type, List.of());
    }

    private static RoleScope customRole(Long... deptIds) {
        return new RoleScope(ScopeType.CUSTOM, List.of(deptIds));
    }

    // ── (a) 单角色 ALL ⇒ unlimited ─────────────────────────────────────────

    @Test
    @DisplayName("(a) 单角色 ALL ⇒ unlimited，且 SQL 为 null（不限）")
    void allScopeShouldBeUnlimited() {
        ResolvedScope scope = DataScopeCalculator.resolve(List.of(role(ScopeType.ALL)), 2L, deptTree());

        assertThat(scope.unlimited()).isTrue();
        assertThat(scope.self()).isFalse();
        assertThat(scope.deptIds()).isEmpty();
        assertThat(scope.isEmpty()).isFalse();
        assertThat(DataScopeCalculator.toSql(scope, 9L, "id", "dept_id")).isNull();
    }

    // ── (b) 多角色取并集（最大范围）────────────────────────────────────────

    @Test
    @DisplayName("(b) 多角色 DEPT + SELF ⇒ 部门并集 + 本人标记（并集，不是交集）")
    void multipleRolesShouldBeUnion() {
        ResolvedScope scope = DataScopeCalculator.resolve(
            List.of(role(ScopeType.DEPT), role(ScopeType.SELF)), 2L, deptTree());

        assertThat(scope.unlimited()).isFalse();
        assertThat(scope.self()).isTrue();
        assertThat(scope.deptIds()).containsExactly(2L);
        assertThat(DataScopeCalculator.toSql(scope, 9L, "id", "dept_id"))
            .isEqualTo("(dept_id IN (2) OR id = 9)");
    }

    @Test
    @DisplayName("(b-2) 多角色 DEPT + DEPT_AND_CHILD ⇒ 部门取并集（大范围覆盖小范围）")
    void multipleDeptRolesShouldUnionDeptIds() {
        ResolvedScope scope = DataScopeCalculator.resolve(
            List.of(role(ScopeType.DEPT), role(ScopeType.DEPT_AND_CHILD)), 2L, deptTree());

        // 单角色 DEPT 只给 {2}；与 DEPT_AND_CHILD（{2,4}）取并集仍应为 {2,4}（若取交集则退化为 {2}）
        assertThat(scope.deptIds()).containsExactly(2L, 4L);
    }

    @Test
    @DisplayName("(b-3) 并集与角色顺序无关（交换律）：正序与倒序结果一致")
    void unionShouldBeOrderIndependent() {
        List<RoleScope> forward = List.of(
            new RoleScope(ScopeType.DEPT, List.of()),
            new RoleScope(ScopeType.SELF, List.of()),
            customRole(5L));
        List<RoleScope> reversed = List.of(customRole(5L), role(ScopeType.SELF), role(ScopeType.DEPT));

        ResolvedScope byForward = DataScopeCalculator.resolve(forward, 2L, deptTree());
        ResolvedScope byReversed = DataScopeCalculator.resolve(reversed, 2L, deptTree());

        // 并集必须交换律成立；若实现退化成「按顺序取交集/单向覆盖」，两者会不一致
        assertThat(byForward.deptIds()).isEqualTo(byReversed.deptIds());
        assertThat(byForward.deptIds()).containsExactlyInAnyOrder(2L, 5L);
        assertThat(byForward.self()).isTrue();
        assertThat(byReversed.self()).isTrue();
    }

    @Test
    @DisplayName("(b-4) 任一角色 ALL ⇒ 整体不限（最大范围优先）")
    void anyAllRoleShouldDominate() {
        ResolvedScope scope = DataScopeCalculator.resolve(
            List.of(role(ScopeType.DEPT), role(ScopeType.ALL), role(ScopeType.SELF)), 2L, deptTree());

        assertThat(scope.unlimited()).isTrue();
        assertThat(scope.self()).isFalse();
        assertThat(scope.deptIds()).isEmpty();
    }

    // ── (c) DEPT_AND_CHILD 含全部后代 ──────────────────────────────────────

    @Test
    @DisplayName("(c) DEPT_AND_CHILD ⇒ 本部门 + 全部后代")
    void deptAndChildShouldIncludeDescendants() {
        ResolvedScope scope = DataScopeCalculator.resolve(
            List.of(role(ScopeType.DEPT_AND_CHILD)), 1L, deptTree());

        assertThat(scope.deptIds()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
        assertThat(scope.self()).isFalse();
    }

    @Test
    @DisplayName("(c-2) DEPT 只含本部门、不含后代")
    void deptShouldNotIncludeDescendants() {
        ResolvedScope scope = DataScopeCalculator.resolve(List.of(role(ScopeType.DEPT)), 1L, deptTree());

        assertThat(scope.deptIds()).containsExactly(1L);
    }

    // ── (d) CUSTOM ⇒ 绑定部门 + 其后代 ────────────────────────────────────

    @Test
    @DisplayName("(d) CUSTOM ⇒ 绑定部门 + 其后代")
    void customShouldIncludeBoundDeptsAndDescendants() {
        ResolvedScope scope = DataScopeCalculator.resolve(List.of(customRole(2L, 5L)), 1L, deptTree());

        assertThat(scope.deptIds()).containsExactlyInAnyOrder(2L, 4L, 5L);
        assertThat(scope.self()).isFalse();
    }

    @Test
    @DisplayName("(d-2) 同角色多份 CUSTOM 声明、以及 DEPT+SELF+CUSTOM 组合均取并集")
    void customCombinedWithOtherRoles() {
        ResolvedScope scope = DataScopeCalculator.resolve(
            List.of(customRole(3L), role(ScopeType.SELF), role(ScopeType.DEPT)), 1L, deptTree());

        assertThat(scope.deptIds()).containsExactlyInAnyOrder(1L, 3L);
        assertThat(scope.self()).isTrue();
    }

    // ── (e) 未知/脏 code 该角色不贡献 ─────────────────────────────────────

    @Test
    @DisplayName("(e) UNKNOWN 角色不贡献任何范围")
    void unknownScopeShouldContributeNothing() {
        ResolvedScope onlyUnknown = DataScopeCalculator.resolve(
            List.of(role(ScopeType.UNKNOWN)), 1L, deptTree());

        assertThat(onlyUnknown.isEmpty()).isTrue();
        assertThat(onlyUnknown.deptIds()).isEmpty();
        assertThat(onlyUnknown.self()).isFalse();
        // 空范围 ⇒ 拒绝全部，不是「不限」
        assertThat(DataScopeCalculator.toSql(onlyUnknown, 9L, "id", "dept_id")).isEqualTo("id = -1");

        // 与有效角色混合时，脏角色不放大也不缩小其它角色的范围
        ResolvedScope mixed = DataScopeCalculator.resolve(
            List.of(role(ScopeType.UNKNOWN), role(ScopeType.DEPT)), 3L, deptTree());
        assertThat(mixed.deptIds()).containsExactly(3L);
    }

    @Test
    @DisplayName("(e-2) 脏 code 经 fromCode 解析为 UNKNOWN；合法 code 解析出对应类型")
    void fromCodeShouldParseCodes() {
        assertThat(ScopeType.fromCode(1)).isEqualTo(ScopeType.ALL);
        assertThat(ScopeType.fromCode(2)).isEqualTo(ScopeType.DEPT_AND_CHILD);
        assertThat(ScopeType.fromCode(3)).isEqualTo(ScopeType.DEPT);
        assertThat(ScopeType.fromCode(4)).isEqualTo(ScopeType.SELF);
        assertThat(ScopeType.fromCode(5)).isEqualTo(ScopeType.CUSTOM);
        assertThat(ScopeType.fromCode(0)).isEqualTo(ScopeType.UNKNOWN);
        assertThat(ScopeType.fromCode(6)).isEqualTo(ScopeType.UNKNOWN);
        assertThat(ScopeType.fromCode(-1)).isEqualTo(ScopeType.UNKNOWN);
        assertThat(ScopeType.fromCode(null)).isEqualTo(ScopeType.UNKNOWN);

        // code 与 sys_role.data_scope 契约一致，不得随意调整
        assertThat(ScopeType.ALL.getCode()).isEqualTo(1);
        assertThat(ScopeType.DEPT_AND_CHILD.getCode()).isEqualTo(2);
        assertThat(ScopeType.DEPT.getCode()).isEqualTo(3);
        assertThat(ScopeType.SELF.getCode()).isEqualTo(4);
        assertThat(ScopeType.CUSTOM.getCode()).isEqualTo(5);
        assertThat(ScopeType.UNKNOWN.getCode()).isZero();
        assertThat(ScopeType.UNKNOWN.getDescription()).isNotBlank();
    }

    @Test
    @DisplayName("(e-3) RoleScope 入参规整：null 类型按 UNKNOWN、null 部门列表按空集合")
    void roleScopeShouldNormalizeNulls() {
        RoleScope nullType = new RoleScope(null, null);

        assertThat(nullType.type()).isEqualTo(ScopeType.UNKNOWN);
        assertThat(nullType.customDeptIds()).isEmpty();
        assertThat(DataScopeCalculator.resolve(List.of(nullType), 1L, deptTree()).isEmpty()).isTrue();
    }

    // ── (f) 无角色 ⇒ denyAll ───────────────────────────────────────────────

    @Test
    @DisplayName("(f) 无角色（null / 空列表）⇒ denyAll")
    void noRolesShouldDenyAll() {
        assertThat(DataScopeCalculator.resolve(List.of(), 1L, deptTree()).isEmpty()).isTrue();
        assertThat(DataScopeCalculator.resolve(null, 1L, deptTree()).isEmpty()).isTrue();
        assertThat(ResolvedScope.denyAll().isEmpty()).isTrue();
        assertThat(ResolvedScope.denyAll().unlimited()).isFalse();
        assertThat(ResolvedScope.denyAll().self()).isFalse();
        assertThat(ResolvedScope.denyAll().deptIds()).isEmpty();
    }

    @Test
    @DisplayName("(f-2) 角色列表含 null 元素时跳过而不失败")
    void nullRoleElementShouldBeSkipped() {
        List<RoleScope> roles = new ArrayList<>();
        roles.add(null);
        roles.add(role(ScopeType.DEPT));

        assertThat(DataScopeCalculator.resolve(roles, 2L, deptTree()).deptIds()).containsExactly(2L);
    }

    // ── (g) 空 deptId ⇒ 读路径 id = -1、写路径拒绝 ─────────────────────────

    @Test
    @DisplayName("(g) 空 deptId + 部门范围角色 ⇒ 空范围，读路径 id = -1（写路径见 AbstractDataScopeHandlerTest）")
    void nullUserDeptIdShouldDenyAll() {
        for (ScopeType type : List.of(ScopeType.DEPT, ScopeType.DEPT_AND_CHILD)) {
            ResolvedScope scope = DataScopeCalculator.resolve(List.of(role(type)), null, deptTree());

            assertThat(scope.isEmpty()).as("范围类型 %s 在无部门时应为空", type).isTrue();
            assertThat(DataScopeCalculator.toSql(scope, 9L, "id", "dept_id"))
                .as("范围类型 %s 的读路径片段", type)
                .isEqualTo("id = -1");
        }
    }

    @Test
    @DisplayName("(g-2) 空 deptId 但角色含 SELF ⇒ 仍可看本人（不是拒绝全部）")
    void nullUserDeptIdWithSelfRoleShouldStillSeeSelf() {
        ResolvedScope scope = DataScopeCalculator.resolve(List.of(role(ScopeType.SELF)), null, deptTree());

        assertThat(scope.isEmpty()).isFalse();
        assertThat(DataScopeCalculator.toSql(scope, 42L, "id", "dept_id")).isEqualTo("id = 42");
    }

    // ── (h) 部门数据成环 ⇒ 正常返回、不死循环 ──────────────────────────────

    @Test
    @DisplayName("(h) 部门数据成环 ⇒ collectDescendants 正常返回已访问子集，不栈溢出")
    void collectDescendantsShouldSurviveCycle() {
        // 1 → 2 → 3 → 1 的环（两次编辑即可造出，单靠「父不能是自己」拦不住）
        Map<Long, List<Long>> cyclic = new HashMap<>();
        cyclic.put(1L, new ArrayList<>(List.of(2L)));
        cyclic.put(2L, new ArrayList<>(List.of(3L)));
        cyclic.put(3L, new ArrayList<>(List.of(1L)));

        Set<Long> descendants = DataScopeCalculator.collectDescendants(1L, cyclic);

        assertThat(descendants).containsExactly(2L, 3L);
    }

    @Test
    @DisplayName("(h-2) 自环 + 空入参 ⇒ 不爆栈、返回空集合")
    void collectDescendantsEdgeCases() {
        Map<Long, List<Long>> selfLoop = new HashMap<>();
        selfLoop.put(7L, new ArrayList<>(List.of(7L)));

        assertThat(DataScopeCalculator.collectDescendants(7L, selfLoop)).isEmpty();
        assertThat(DataScopeCalculator.collectDescendants(null, selfLoop)).isEmpty();
        assertThat(DataScopeCalculator.collectDescendants(1L, null)).isEmpty();
        assertThat(DataScopeCalculator.collectDescendants(1L, Map.of())).isEmpty();
        // 中间某父节点没有子列表（缺口）也要正常结束
        Map<Long, List<Long>> sparse = new HashMap<>();
        sparse.put(1L, new ArrayList<>(List.of(2L)));
        assertThat(DataScopeCalculator.collectDescendants(1L, sparse)).containsExactly(2L);
        // 子列表里的 null 元素（脏数据）跳过
        Map<Long, List<Long>> withNull = new HashMap<>();
        withNull.put(1L, new ArrayList<>(List.of(2L)));
        withNull.get(1L).add(null);
        assertThat(DataScopeCalculator.collectDescendants(1L, withNull)).containsExactly(2L);
    }

    @Test
    @DisplayName("(h-3) 深层部门链（10 万层）不栈溢出：迭代实现")
    void collectDescendantsShouldHandleDeepChain() {
        int depth = 100_000;
        Map<Long, List<Long>> chain = new HashMap<>();
        for (long id = 1; id <= depth; id++) {
            chain.put(id, new ArrayList<>(List.of(id + 1)));
        }
        chain.put((long) depth + 1, new ArrayList<>());

        Set<Long> descendants = DataScopeCalculator.collectDescendants(1L, chain);

        assertThat(descendants).hasSize(depth);
    }

    // ── toSql 片段拼装 ────────────────────────────────────────────────────

    @Test
    @DisplayName("toSql：部门片段、本人片段、两者 OR 组合")
    void toSqlShouldAssembleFragments() {
        ResolvedScope deptOnly = new ResolvedScope(false, false, new LinkedHashSet<>(List.of(1L, 2L)));
        assertThat(DataScopeCalculator.toSql(deptOnly, 9L, "id", "dept_id")).isEqualTo("dept_id IN (1,2)");

        ResolvedScope selfOnly = new ResolvedScope(false, true, Set.of());
        assertThat(DataScopeCalculator.toSql(selfOnly, 9L, "id", "dept_id")).isEqualTo("id = 9");

        ResolvedScope both = new ResolvedScope(false, true, new LinkedHashSet<>(List.of(1L, 2L)));
        assertThat(DataScopeCalculator.toSql(both, 9L, "id", "dept_id"))
            .isEqualTo("(dept_id IN (1,2) OR id = 9)");

        // 自定义列名
        assertThat(DataScopeCalculator.toSql(both, 9L, "user_id", "org_id"))
            .isEqualTo("(org_id IN (1,2) OR user_id = 9)");
    }

    @Test
    @DisplayName("toSql：列名必须是普通标识符（防外部输入拼进 SQL）")
    void toSqlShouldRejectIllegalColumnNames() {
        ResolvedScope scope = new ResolvedScope(false, true, new LinkedHashSet<>(List.of(1L)));

        assertThatThrownBy(() -> DataScopeCalculator.toSql(scope, 9L, "id; DROP TABLE x", "dept_id"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("idColumn");
        assertThatThrownBy(() -> DataScopeCalculator.toSql(scope, 9L, null, "dept_id"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DataScopeCalculator.toSql(scope, 9L, "id", "dept_id desc"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("deptColumn");
        assertThatThrownBy(() -> DataScopeCalculator.denyAllSql("1id"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(DataScopeCalculator.DEFAULT_ID_COLUMN).isEqualTo("id");
        assertThat(DataScopeCalculator.DEFAULT_DEPT_COLUMN).isEqualTo("dept_id");
    }

    @Test
    @DisplayName("toSql：scope / userId 为 null 属调用方 bug，显式失败")
    void toSqlShouldRejectNullArguments() {
        ResolvedScope scope = new ResolvedScope(false, true, new LinkedHashSet<>(List.of(1L)));

        assertThatThrownBy(() -> DataScopeCalculator.toSql(null, 9L, "id", "dept_id"))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> DataScopeCalculator.toSql(scope, null, "id", "dept_id"))
            .isInstanceOf(NullPointerException.class);
    }

    // ── ResolvedScope 不可变性 ────────────────────────────────────────────

    @Test
    @DisplayName("ResolvedScope 不可变：外部集合改动不影响结果，返回集合不可写")
    void resolvedScopeShouldBeImmutable() {
        List<Long> mutableDepts = new ArrayList<>(List.of(1L, 2L));
        ResolvedScope scope = new ResolvedScope(false, false, new LinkedHashSet<>(mutableDepts));

        mutableDepts.add(3L);
        assertThat(scope.deptIds()).containsExactly(1L, 2L);
        assertThatThrownBy(() -> scope.deptIds().add(4L)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new ResolvedScope(false, false, null))
            .isInstanceOf(NullPointerException.class);
        // 顺序按插入顺序保留，使 SQL 片段可复现
        assertThat(DataScopeCalculator.toSql(
            new ResolvedScope(false, false, new LinkedHashSet<>(List.of(3L, 1L, 2L))), 9L, "id", "dept_id"))
            .isEqualTo("dept_id IN (3,1,2)");
    }

    @Test
    @DisplayName("RoleScope 对自定义部门做防御性拷贝")
    void roleScopeShouldDefensivelyCopyCustomDeptIds() {
        List<Long> mutableDepts = new ArrayList<>(List.of(2L));
        RoleScope role = new RoleScope(ScopeType.CUSTOM, mutableDepts);

        mutableDepts.add(5L);

        assertThat(role.customDeptIds()).containsExactly(2L);
        assertThatThrownBy(() -> role.customDeptIds().add(6L)).isInstanceOf(UnsupportedOperationException.class);
    }
}

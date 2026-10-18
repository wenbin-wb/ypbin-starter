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

import cn.ypbin.starter.datapermission.core.DataScopeCalculator.RoleScope;
import cn.ypbin.starter.datapermission.core.DataScopeCalculator.ScopeType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AbstractDataScopeHandler} 单元测试。
 *
 * <p>覆盖 issue #107 验收 2：重入防护（解析自身查询回调返回 {@code null}）、超管短路、
 * 无身份头 ⇒ 拒绝全部；并覆盖写路径 {@code isDeptWithinScope} 与「无角色 / 部门为空」的 fail-closed 语义。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
class AbstractDataScopeHandlerTest {

    private static final String MS_ID = "com.demo.UserMapper.selectList";
    private static final String TABLE = "sys_user";

    /**
     * 可编程的测试宿主处理器：把 5 个抽象方法映射成可注入的返回值与钩子。
     */
    private static class FakeHandler extends AbstractDataScopeHandler {

        @Nullable
        private Long userId = 9L;

        @Nullable
        private Long userDeptId = 2L;

        private boolean superAdmin;

        private List<RoleScope> roles = List.of(new RoleScope(ScopeType.DEPT, List.of()));

        private Map<Long, List<Long>> deptChildren = Map.of();

        private final AtomicInteger roleLoads = new AtomicInteger();

        private final AtomicInteger deptLoads = new AtomicInteger();

        /** 解析期间回调自身（模拟「角色查询也带 @DataPermission」）。 */
        private boolean callbackWhileResolving;

        @Nullable
        private String sqlDuringCallback;

        @Override
        protected Optional<Long> currentUserId() {
            return Optional.ofNullable(userId);
        }

        @Override
        @Nullable
        protected Long currentUserDeptId() {
            return userDeptId;
        }

        @Override
        protected boolean isSuperAdmin(long id) {
            return superAdmin;
        }

        @Override
        protected List<RoleScope> loadRoleScopes(long id) {
            roleLoads.incrementAndGet();
            if (callbackWhileResolving) {
                // 解析过程中的查询再次触发处理器：必须被重入防护短路为 null
                sqlDuringCallback = getDataScopeSql(MS_ID, TABLE);
            }
            return roles;
        }

        @Override
        protected Map<Long, List<Long>> loadDeptChildren() {
            deptLoads.incrementAndGet();
            return deptChildren;
        }
    }

    private static Map<Long, List<Long>> deptTree() {
        Map<Long, List<Long>> children = new HashMap<>();
        children.put(2L, new ArrayList<>(List.of(21L, 22L)));
        children.put(21L, new ArrayList<>());
        children.put(22L, new ArrayList<>());
        return children;
    }

    // ── 读路径：基本语义 ──────────────────────────────────────────────────

    @Test
    @DisplayName("DEPT 角色 ⇒ dept_id IN (本部门)")
    void deptScopeShouldProduceDeptCondition() {
        FakeHandler handler = new FakeHandler();

        assertThat(handler.getDataScopeSql(MS_ID, TABLE)).isEqualTo("dept_id IN (2)");
    }

    @Test
    @DisplayName("DEPT_AND_CHILD ⇒ 含后代；仅此时才会加载部门树")
    void deptAndChildShouldLoadTreeOnlyWhenNeeded() {
        FakeHandler handler = new FakeHandler();
        handler.roles = List.of(new RoleScope(ScopeType.DEPT_AND_CHILD, List.of()));
        handler.deptChildren = deptTree();

        assertThat(handler.getDataScopeSql(MS_ID, TABLE)).isEqualTo("dept_id IN (2,21,22)");
        assertThat(handler.deptLoads.get()).isEqualTo(1);

        FakeHandler deptOnly = new FakeHandler();
        deptOnly.getDataScopeSql(MS_ID, TABLE);
        assertThat(deptOnly.deptLoads.get()).as("DEPT 角色不需要部门树，不得查库").isZero();
    }

    @Test
    @DisplayName("ALL 角色 ⇒ 返回 null（不限）")
    void allRoleShouldBeUnrestricted() {
        FakeHandler handler = new FakeHandler();
        handler.roles = List.of(new RoleScope(ScopeType.ALL, List.of()));

        assertThat(handler.getDataScopeSql(MS_ID, TABLE)).isNull();
    }

    // ── 重入防护 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("重入防护：解析自身查询回调 getDataScopeSql 时返回 null，且解析标记被清理")
    void reentrantCallDuringResolveShouldReturnNull() {
        FakeHandler handler = new FakeHandler();
        handler.callbackWhileResolving = true;

        String sql = handler.getDataScopeSql(MS_ID, TABLE);

        assertThat(handler.sqlDuringCallback).as("解析期间的查询不得叠加数据范围").isNull();
        assertThat(sql).isEqualTo("dept_id IN (2)");
        assertThat(AbstractDataScopeHandler.RESOLVING.get()).as("ThreadLocal 必须被清理").isNull();
    }

    @Test
    @DisplayName("重入防护：解析抛异常时 ThreadLocal 也必须清理（不污染同线程后续请求）")
    void resolvingFlagShouldBeClearedOnException() {
        FakeHandler handler = new FakeHandler() {
            @Override
            protected List<RoleScope> loadRoleScopes(long id) {
                throw new IllegalStateException("模拟角色查询失败");
            }
        };

        try {
            handler.getDataScopeSql(MS_ID, TABLE);
        } catch (IllegalStateException ex) {
            // 异常必须原样上抛（禁静默降级）
            assertThat(ex).hasMessageContaining("模拟角色查询失败");
        }

        assertThat(AbstractDataScopeHandler.RESOLVING.get()).isNull();
    }

    // ── 超管短路 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("超管 ⇒ 读路径不限、写路径放行，且不查角色/部门树")
    void superAdminShouldBypassScope() {
        FakeHandler handler = new FakeHandler();
        handler.superAdmin = true;

        assertThat(handler.getDataScopeSql(MS_ID, TABLE)).isNull();
        assertThat(handler.isDeptWithinScope(999L)).isTrue();
        assertThat(handler.roleLoads.get()).as("超管判定后应短路，不加载角色").isZero();
    }

    // ── 无身份头 ⇒ 拒绝全部 ───────────────────────────────────────────────

    @Test
    @DisplayName("无身份头（取不到 userId）⇒ 读路径 id = -1、写路径拒绝")
    void missingIdentityShouldDenyAll() {
        FakeHandler handler = new FakeHandler();
        handler.userId = null;

        assertThat(handler.getDataScopeSql(MS_ID, TABLE)).isEqualTo("id = -1");
        assertThat(handler.isDeptWithinScope(2L)).isFalse();
        assertThat(handler.roleLoads.get()).isZero();
    }

    // ── 无角色 / 部门为空 ⇒ fail-closed ───────────────────────────────────

    @Test
    @DisplayName("无角色 ⇒ 读路径拒绝全部、写路径拒绝")
    void noRolesShouldDenyAll() {
        FakeHandler handler = new FakeHandler();
        handler.roles = List.of();

        assertThat(handler.getDataScopeSql(MS_ID, TABLE)).isEqualTo("id = -1");
        assertThat(handler.isDeptWithinScope(2L)).isFalse();
    }

    @Test
    @DisplayName("脏 code（UNKNOWN）⇒ 该角色不贡献 ⇒ 拒绝全部")
    void unknownRoleShouldContributeNothing() {
        FakeHandler handler = new FakeHandler();
        handler.roles = List.of(new RoleScope(ScopeType.UNKNOWN, List.of()));

        assertThat(handler.getDataScopeSql(MS_ID, TABLE)).isEqualTo("id = -1");
    }

    @Test
    @DisplayName("部门范围角色 + 用户无部门 ⇒ 读路径拒绝全部、写路径拒绝")
    void nullUserDeptShouldDenyAllForDeptRoles() {
        FakeHandler handler = new FakeHandler();
        handler.userDeptId = null;

        assertThat(handler.getDataScopeSql(MS_ID, TABLE)).isEqualTo("id = -1");
        assertThat(handler.isDeptWithinScope(null)).isFalse();
        assertThat(handler.isDeptWithinScope(2L)).isFalse();
    }

    // ── 写路径部门归属校验 ────────────────────────────────────────────────

    @Test
    @DisplayName("写路径：目标部门在可见集合内放行、不在则拒绝、为 null 一律拒绝")
    void isDeptWithinScopeShouldCheckVisibility() {
        FakeHandler handler = new FakeHandler();
        handler.roles = List.of(new RoleScope(ScopeType.DEPT_AND_CHILD, List.of()));
        handler.deptChildren = deptTree();

        assertThat(handler.isDeptWithinScope(2L)).isTrue();
        assertThat(handler.isDeptWithinScope(21L)).isTrue();
        assertThat(handler.isDeptWithinScope(99L)).isFalse();
        // 读路径条件为 dept_id IN (...)，NULL 不匹配任何部门 ⇒ 写入后永远读不到，必须拒绝
        assertThat(handler.isDeptWithinScope(null)).isFalse();
    }

    @Test
    @DisplayName("写路径：仅 SELF 角色时部门集合为空 ⇒ 任何部门都拒绝")
    void selfOnlyRoleShouldRejectAnyDept() {
        FakeHandler handler = new FakeHandler();
        handler.roles = List.of(new RoleScope(ScopeType.SELF, List.of()));

        assertThat(handler.getDataScopeSql(MS_ID, TABLE)).isEqualTo("id = 9");
        assertThat(handler.isDeptWithinScope(2L)).isFalse();
    }

    @Test
    @DisplayName("loadDeptChildren 返回 null 属实现违约，显式失败而不是静默当作空树")
    void nullDeptChildrenShouldFailFast() {
        FakeHandler handler = new FakeHandler() {
            @Override
            protected Map<Long, List<Long>> loadDeptChildren() {
                return null;
            }
        };
        handler.roles = List.of(new RoleScope(ScopeType.DEPT_AND_CHILD, List.of()));

        assertThatThrownBy(() -> handler.getDataScopeSql(MS_ID, TABLE))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("loadDeptChildren");
    }
}

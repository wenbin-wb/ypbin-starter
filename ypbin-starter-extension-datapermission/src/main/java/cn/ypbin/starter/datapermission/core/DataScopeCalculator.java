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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * 数据范围算法（纯逻辑：零 IO、零 Spring）。
 *
 * <p>把「当前用户的角色数据范围声明」合并成一个不可变结果，并拼装成 SQL 条件片段。
 * 本类<b>不认识</b>任何实体/表结构/Mapper：角色码、用户部门、部门父子关系都由调用方以值的形式传入，
 * 因而可以在任何宿主（admin / iot / 未来的其它后台系统）复用同一份口径，也可脱离 Spring 单测。</p>
 *
 * <p><b>算法语义（RBAC 事实标准，与 {@code sys_role.data_scope} 的 1/2/3/4/5 一致）</b>：</p>
 * <ol>
 *   <li><b>多角色取并集（最大范围）</b>：任一角色为「全部」⇒ 不受限；否则把各角色贡献的部门
 *       与「仅本人」标记合并。<b>绝不能取交集</b>——取交集会让「一个角色管全公司 + 一个角色管本部门」
 *       的用户只剩本部门可见，权限被凭空收窄，属安全/可用性双重缺陷。</li>
 *   <li>{@code DEPT_AND_CHILD} = 本部门 + 部门树全部后代；{@code CUSTOM} = 角色绑定部门 + 其后代
 *       （与前端勾选部门树「勾中即含下级」的语义一致）。</li>
 *   <li>{@code DEPT} = 仅本部门；{@code SELF} = 仅本人（拼 {@code id = ?}）。</li>
 *   <li><b>未知/脏 code（{@link ScopeType#UNKNOWN}）</b>：该角色<b>不贡献</b>任何范围（收窄而非放大），
 *       是否留痕由调用方决定（本类保持纯函数，不直接写日志）。</li>
 *   <li><b>无角色</b>，或「部门范围角色但用户没有部门」⇒ 解析结果为空 ⇒ 读路径拒绝全部
 *       （{@code id = -1}）、写路径拒绝。</li>
 *   <li><b>部门数据成环</b>：{@link #collectDescendants} 用「已访问集合 + 迭代」兜底，
 *       对合法树行为不变，对环返回已访问子集而不是栈溢出。</li>
 * </ol>
 *
 * <p><strong>本类的 SQL 片段只能用于单表查询</strong>：片段不含表别名（starter 的
 * {@link DataScopeHandler} 只把表名交给宿主，没有别名信息），在 JOIN 中出现同名列会有歧义。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
public final class DataScopeCalculator {

    /** 默认主键列名。 */
    public static final String DEFAULT_ID_COLUMN = "id";

    /** 默认部门列名。 */
    public static final String DEFAULT_DEPT_COLUMN = "dept_id";

    /**
     * 拒绝全部时使用的主键值：主键不可能等于 -1，等价「查不到任何行」。
     *
     * <p>用「主键恒假」而不是 {@code 1 = 0}，是为了让生成的 SQL 仍带可用的列引用
     * （部分数据库/优化器对常量假条件有特殊处理，且日志里能直接看出治理的是哪张表）。</p>
     */
    private static final long DENY_ALL_ID = -1L;

    /** 列名合法性：只允许普通标识符，避免把外部输入直接拼进 SQL。 */
    private static final Pattern COLUMN_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private DataScopeCalculator() {
    }

    /**
     * 角色数据范围类型。
     *
     * <p>{@code code} 与 {@code sys_role.data_scope} 列取值一一对应（1 全部、2 本部门及以下、
     * 3 本部门、4 仅本人、5 自定义）；解析一律走 {@link #fromCode(Integer)}，
     * 禁止在业务代码里散落裸数字。{@link #UNKNOWN} 不映射任何库值，仅表示「无法识别的脏值」。</p>
     */
    public enum ScopeType {

        /** 全部数据（不受部门约束）。 */
        ALL(1, "全部数据"),

        /** 本部门及以下数据。 */
        DEPT_AND_CHILD(2, "本部门及以下数据"),

        /** 本部门数据。 */
        DEPT(3, "本部门数据"),

        /** 仅本人数据。 */
        SELF(4, "仅本人数据"),

        /** 自定义数据（部门取自角色-部门关联，含后代）。 */
        CUSTOM(5, "自定义数据"),

        /** 未知/脏值：该角色不贡献任何范围。 */
        UNKNOWN(0, "无法识别");

        private final int code;
        private final String description;

        ScopeType(int code, String description) {
            this.code = code;
            this.description = description;
        }

        /**
         * 库中存储的编码。
         *
         * @return 编码（{@link #UNKNOWN} 为 0，不对应任何库值）
         */
        public int getCode() {
            return code;
        }

        /**
         * 描述（用于日志/展示）。
         *
         * @return 描述
         */
        public String getDescription() {
            return description;
        }

        /**
         * 按编码解析数据范围。
         *
         * <p>{@code null}、未匹配的脏值一律返回 {@link #UNKNOWN}（<b>不抛异常</b>）：
         * 数据脏不应让整条查询失败，而应「该角色不贡献范围 + 调用方留痕」。</p>
         *
         * @param code 编码，可为 {@code null}
         * @return 对应的范围类型，无法识别时为 {@link #UNKNOWN}
         */
        public static ScopeType fromCode(@Nullable Integer code) {
            if (code == null) {
                return UNKNOWN;
            }
            for (ScopeType type : values()) {
                if (type != UNKNOWN && type.code == code) {
                    return type;
                }
            }
            return UNKNOWN;
        }
    }

    /**
     * 输入：一个角色声明的数据范围。
     *
     * @param type          范围类型（{@code null} 按 {@link ScopeType#UNKNOWN} 处理）
     * @param customDeptIds 自定义部门 ID（仅 {@link ScopeType#CUSTOM} 使用；{@code null} 按空集合处理）
     */
    public record RoleScope(ScopeType type, List<Long> customDeptIds) {

        /**
         * 规整入参：类型缺省为 {@link ScopeType#UNKNOWN}，部门列表做不可变防御性拷贝。
         */
        public RoleScope {
            if (type == null) {
                type = ScopeType.UNKNOWN;
            }
            // 集合禁 null：null 视为空；元素不得为 null（List.copyOf 会快速失败，脏数据不静默吞掉）
            customDeptIds = customDeptIds == null ? List.of() : List.copyOf(customDeptIds);
        }
    }

    /**
     * 输出：合并后的数据范围（不可变）。
     *
     * @param unlimited 是否不受限（全部数据）
     * @param self      是否包含「仅本人」
     * @param deptIds   可见部门 ID 集合（含按树展开的后代；保持插入顺序以便 SQL 片段稳定）
     */
    public record ResolvedScope(boolean unlimited, boolean self, Set<Long> deptIds) {

        /**
         * 规整入参：部门集合做不可变防御性拷贝并保持插入顺序（SQL 片段因此可复现）。
         */
        public ResolvedScope {
            Objects.requireNonNull(deptIds, "deptIds");
            deptIds = Collections.unmodifiableSet(new LinkedHashSet<>(deptIds));
        }

        /**
         * 拒绝全部（不可见任何数据）。
         *
         * @return 空范围
         */
        public static ResolvedScope denyAll() {
            return new ResolvedScope(false, false, Set.of());
        }

        /**
         * 是否空范围（不可见任何数据、也不含「仅本人」）。
         *
         * @return {@code true} 表示应拒绝全部
         */
        public boolean isEmpty() {
            return !unlimited && !self && deptIds.isEmpty();
        }
    }

    /**
     * 合并多角色范围（取并集/最大范围）。
     *
     * <p>任一角色为 {@link ScopeType#ALL} ⇒ 立即返回不受限；否则累加各部门范围与「仅本人」。
     * 角色列表为空（或全为 {@link ScopeType#UNKNOWN}）⇒ {@link ResolvedScope#denyAll()}。
     * 需要部门树展开时才读取 {@code deptChildren}，只给部门范围的角色时传空 Map 也能工作。</p>
     *
     * @param roles        当前用户的全部角色范围声明，可为 {@code null}（按无角色处理）
     * @param userDeptId   用户所属部门，可为 {@code null}
     * @param deptChildren 父部门 ID → 子部门 ID 列表，可为 {@code null}（按空 Map 处理）
     * @return 合并后的不可变范围
     */
    public static ResolvedScope resolve(@Nullable Collection<RoleScope> roles, @Nullable Long userDeptId,
            @Nullable Map<Long, List<Long>> deptChildren) {
        if (roles == null || roles.isEmpty()) {
            // 无角色：不可见任何数据（fail-closed），调用方应记 WARN 暴露「用户没有任何有效角色」
            return ResolvedScope.denyAll();
        }
        Set<Long> deptIds = new LinkedHashSet<>();
        boolean self = false;
        for (RoleScope role : roles) {
            if (role == null) {
                continue;
            }
            ScopeType type = role.type();
            switch (type) {
                case ALL -> {
                    // 最大范围优先：其它角色的部门/本人范围被完全覆盖
                    return new ResolvedScope(true, false, Set.of());
                }
                case DEPT_AND_CHILD -> {
                    if (userDeptId != null) {
                        deptIds.add(userDeptId);
                        deptIds.addAll(collectDescendants(userDeptId, deptChildren));
                    }
                    // 用户没有部门：该角色不贡献（与宿主语义一致），最终可能落到「拒绝全部」
                }
                case DEPT -> {
                    if (userDeptId != null) {
                        deptIds.add(userDeptId);
                    }
                }
                case CUSTOM -> {
                    for (Long boundDeptId : role.customDeptIds()) {
                        if (boundDeptId == null) {
                            // 集合元素为 null 属脏数据：跳过该条而不是让整条查询失败
                            continue;
                        }
                        deptIds.add(boundDeptId);
                        // 自定义部门同样向下展开：与前端部门树「勾中即含下级」一致
                        deptIds.addAll(collectDescendants(boundDeptId, deptChildren));
                    }
                }
                case SELF -> self = true;
                case UNKNOWN -> {
                    // 未知/脏 code：该角色不贡献任何范围（收窄而非放大），留痕由调用方负责
                }
                default -> throw new IllegalStateException("未处理的 ScopeType: " + type);
            }
        }
        return new ResolvedScope(false, self, deptIds);
    }

    /**
     * 收集某部门的全部后代部门 ID（不含自身）。
     *
     * <p><b>环防护 + 迭代</b>：{@code sys_dept} 只有单个父 ID 列，业务校验通常只拒绝「父是自己」，
     * 两次编辑仍可能造出环；递归实现会无限递归直至 {@link StackOverflowError}。这里显式维护
     * 已访问集合（起始节点即计入）并用栈迭代，对合法树输出与递归版本一致，对成环数据返回
     * 已访问子集。</p>
     *
     * @param rootId   起始部门 ID，可为 {@code null}（返回空集合）
     * @param children 父部门 ID → 子部门 ID 列表，可为 {@code null}（按空 Map 处理）
     * @return 后代部门 ID（先序、去重）
     */
    public static Set<Long> collectDescendants(@Nullable Long rootId, @Nullable Map<Long, List<Long>> children) {
        Set<Long> descendants = new LinkedHashSet<>();
        if (rootId == null || children == null || children.isEmpty()) {
            return descendants;
        }
        Set<Long> visited = new LinkedHashSet<>();
        visited.add(rootId);
        // 显式栈，避免深层部门树递归爆栈
        ArrayList<Long> pending = new ArrayList<>();
        pending.add(rootId);
        for (int index = 0; index < pending.size(); index++) {
            Long current = pending.get(index);
            List<Long> childIds = children.get(current);
            if (childIds == null) {
                continue;
            }
            for (Long childId : childIds) {
                if (childId != null && visited.add(childId)) {
                    descendants.add(childId);
                    pending.add(childId);
                }
            }
        }
        return descendants;
    }

    /**
     * 拼装数据范围 SQL 条件片段。
     *
     * <p>返回 {@code null} 表示<b>不限</b>（{@code scope.unlimited()}）；范围为空（无角色、或部门范围
     * 角色但用户没有部门）时返回「拒绝全部」片段 {@code id = -1}（fail-closed，绝不静默放行全部）。</p>
     *
     * @param scope      解析结果
     * @param userId     当前用户 ID（「仅本人」时拼 {@code id = userId}）
     * @param idColumn   主键列名（普通标识符）
     * @param deptColumn 部门列名（普通标识符）
     * @return SQL 条件片段；{@code null} 表示不做限制
     */
    @Nullable
    public static String toSql(ResolvedScope scope, Long userId, String idColumn, String deptColumn) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(userId, "userId");
        String id = requireIdentifier(idColumn, "idColumn");
        String dept = requireIdentifier(deptColumn, "deptColumn");
        if (scope.unlimited()) {
            return null;
        }
        List<String> conditions = new ArrayList<>(2);
        if (!scope.deptIds().isEmpty()) {
            StringBuilder deptList = new StringBuilder();
            for (Long deptId : scope.deptIds()) {
                if (deptList.length() > 0) {
                    deptList.append(',');
                }
                deptList.append(deptId);
            }
            conditions.add(dept + " IN (" + deptList + ")");
        }
        if (scope.self()) {
            conditions.add(id + " = " + userId);
        }
        if (conditions.isEmpty()) {
            return denyAllSql(id);
        }
        return conditions.size() == 1 ? conditions.get(0) : "(" + String.join(" OR ", conditions) + ")";
    }

    /**
     * 拒绝全部的 SQL 片段（主键恒假）。
     *
     * @param idColumn 主键列名（普通标识符）
     * @return 形如 {@code id = -1} 的片段
     */
    public static String denyAllSql(String idColumn) {
        return requireIdentifier(idColumn, "idColumn") + " = " + DENY_ALL_ID;
    }

    /**
     * 校验并返回列名：只接受普通标识符，防止调用方把外部输入拼进 SQL。
     */
    private static String requireIdentifier(String column, String name) {
        if (column == null || !COLUMN_NAME.matcher(column).matches()) {
            throw new IllegalArgumentException("非法的 " + name + "（只允许字母/数字/下划线且不以数字开头）: " + column);
        }
        return column;
    }
}

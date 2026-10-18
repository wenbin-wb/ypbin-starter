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

import cn.ypbin.starter.datapermission.core.DataScopeCalculator.ResolvedScope;
import cn.ypbin.starter.datapermission.core.DataScopeCalculator.RoleScope;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 {@link DataScopeCalculator} 的默认 {@link DataScopeHandler} 骨架。
 *
 * <p><b>分工</b>：算法与机制（角色范围并集、部门后代展开、SQL 片段拼装、重入防护、超管短路、
 * 拒绝全部、写路径部门归属校验）全部在本类，且只有一份；宿主只实现 5 个数据源/身份方法：</p>
 * <ul>
 *   <li>{@link #currentUserId()} —— 当前请求用户（如从网关身份头取）；</li>
 *   <li>{@link #currentUserDeptId()} —— 当前用户所在部门（可空）；</li>
 *   <li>{@link #isSuperAdmin(long)} —— 宿主权限模型里的超管判定（starter 无法预设）；</li>
 *   <li>{@link #loadRoleScopes(long)} —— 查角色并把 {@code data_scope} 码解析成 {@link RoleScope}；</li>
 *   <li>{@link #loadDeptChildren()} —— 一次性构建「父部门 → 子部门」索引（避免逐层查库）。</li>
 * </ul>
 *
 * <p><b>为什么是抽象类而不是 Bean</b>：同一能力**不能**出现两个 {@link DataScopeHandler} Bean
 * （会让注入产生歧义）。本类不带 {@code @Component}，由宿主的适配器子类注册为 Bean 即可。</p>
 *
 * <p><b>重入防护</b>：解析范围时宿主要查角色/部门/超管，这些查询若也带 {@code @DataPermission}
 * 就会再次回调本处理器，形成无限递归。本类用线程内标记 {@code RESOLVING} 短路：
 * 解析期间的查询一律返回 {@code null}（不叠加数据范围），见 {@link #RESOLVING}。</p>
 *
 * <p><b>语义</b>（与既有宿主实现保持一致，属 fail-closed）：</p>
 * <ol>
 *   <li>取不到当前用户（无网关身份头）⇒ 读路径拒绝全部（{@code id = -1}）、写路径拒绝，并记 error。
 *       被标注的方法都在鉴权路径上，取不到身份属异常路径；此时「放行全部」会越权、
 *       「静默返回空」会掩盖问题。</li>
 *   <li>超管 ⇒ 读路径不加条件（{@code null}）、写路径放行。租户隔离由租户拦截器独立施加，本类不拼租户条件。</li>
 *   <li>其余按角色范围并集；结果为空（无角色 / 部门范围角色但用户无部门）⇒ 拒绝全部并记 warn。</li>
 *   <li>写路径校验：目标部门必须在可见部门集合内；目标部门为空一律拒绝（读路径是
 *       {@code dept_id IN (...)}，NULL 不匹配任何部门，写进去就再也读不到）。</li>
 * </ol>
 *
 * <p><b>片段只能用于单表查询</b>：{@link DataScopeHandler#getDataScopeSql(String, String)} 的签名里
 * 没有表别名，生成的片段不带别名前缀（详见 {@link DataScopeCalculator} 的说明）。</p>
 *
 * @author wenbin
 * @since 2026-10-18
 */
public abstract class AbstractDataScopeHandler implements DataScopeHandler {

    /**
     * 解析中标记：解析自身所需的数据查询（角色/部门/超管判定常会 JOIN 被治理的表）会再次回调
     * 本处理器，形成无限递归。用线程内标记短路返回 {@code null}。
     */
    protected static final ThreadLocal<Boolean> RESOLVING = new ThreadLocal<>();

    /**
     * 日志器：按<b>运行时类</b>取名，使日志分类落在宿主真正注册的适配器 Bean 上而不是基类，
     * 便于按 Bean 过滤与定位。
     */
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * 当前请求的用户 ID。
     *
     * @return 用户 ID；无身份（如无网关身份头、非请求线程）时为空
     */
    protected abstract Optional<Long> currentUserId();

    /**
     * 当前请求用户所属部门 ID。
     *
     * @return 部门 ID；未知时为 {@code null}
     */
    @Nullable
    protected abstract Long currentUserDeptId();

    /**
     * 是否超级管理员（宿主权限模型，starter 无法预设）。
     *
     * @param userId 用户 ID
     * @return {@code true} 表示不受数据范围限制
     */
    protected abstract boolean isSuperAdmin(long userId);

    /**
     * 解析当前用户的数据范围并集（按需加载部门树）。
     *
     * @param userId 当前用户 ID
     * @return 不可变范围
     */
    private ResolvedScope resolveScope(long userId) {
        List<RoleScope> roles = loadRoleScopes(userId);
        if (roles.isEmpty()) {
            // 无角色：fail-closed；具体留痕在 getDataScopeSql 里随「拒绝全部」一起记
            return ResolvedScope.denyAll();
        }
        boolean needsTree = roles.stream()
            .map(RoleScope::type)
            .anyMatch(type -> type == DataScopeCalculator.ScopeType.DEPT_AND_CHILD
                || type == DataScopeCalculator.ScopeType.CUSTOM);
        Map<Long, List<Long>> deptChildren = needsTree ? Objects.requireNonNull(loadDeptChildren(), "loadDeptChildren()")
            : Map.of();
        return DataScopeCalculator.resolve(roles, currentUserDeptId(), deptChildren);
    }

    @Override
    @Nullable
    public String getDataScopeSql(String mappedStatementId, String tableName) {
        if (Boolean.TRUE.equals(RESOLVING.get())) {
            // 本类解析过程中的查询（角色/部门/超管判定）不再叠加数据范围，避免递归
            return null;
        }
        Optional<Long> userId = currentUserId();
        if (userId.isEmpty()) {
            // 显式拒绝而不是放行：被标注的方法都在鉴权路径上，取不到身份属异常路径
            log.error("数据范围解析失败：当前请求没有网关签发的身份头（取不到用户 ID），"
                + "已按「拒绝全部」处理以免读到全量数据；若该路径本不应受数据权限约束，"
                + "请在该条查询所在的 Mapper 语句上加 @InterceptorIgnore(dataPermission = \"true\")，"
                + "或改用独立 Mapper 语句/不经 Mapper 的通道。mappedStatementId={}, table={}",
                mappedStatementId, tableName);
            return DataScopeCalculator.denyAllSql(DataScopeCalculator.DEFAULT_ID_COLUMN);
        }
        long currentUserId = userId.get();
        ResolvedScope scope;
        RESOLVING.set(Boolean.TRUE);
        try {
            if (isSuperAdmin(currentUserId)) {
                return null;
            }
            scope = resolveScope(currentUserId);
        } finally {
            RESOLVING.remove();
        }
        String sql = DataScopeCalculator.toSql(scope, currentUserId,
            DataScopeCalculator.DEFAULT_ID_COLUMN, DataScopeCalculator.DEFAULT_DEPT_COLUMN);
        if (sql == null) {
            return null;
        }
        if (scope.isEmpty()) {
            log.warn("数据范围解析结果为空（无有效角色，或角色都是部门范围但当前用户没有部门），"
                + "按「拒绝全部」处理：userId={}, mappedStatementId={}, table={}",
                currentUserId, mappedStatementId, tableName);
        }
        return sql;
    }

    /**
     * 写路径：目标部门是否落在当前用户的数据范围内。
     *
     * <p>与读路径共用同一份算法（{@link DataScopeCalculator#resolve}），避免「读得到写不了」
     * 或「写进去读不到」的口径分裂。</p>
     *
     * @param deptId 目标部门 ID（新写入值），可为 {@code null}
     * @return {@code true} 表示允许写入该部门
     */
    public boolean isDeptWithinScope(@Nullable Long deptId) {
        Optional<Long> userId = currentUserId();
        if (userId.isEmpty()) {
            log.error("写路径数据范围校验失败：当前请求没有网关签发的身份头（取不到用户 ID），"
                + "已按「拒绝」处理（不放行全量）。targetDeptId={}", deptId);
            return false;
        }
        if (Boolean.TRUE.equals(RESOLVING.get())) {
            // 结构上不可达（本方法只在写方法里调用，解析自身查询走 getDataScopeSql 已短路）；
            // 保留兜底是为了万一被误用到解析链路时「显式拒绝 + 留痕」而不是无限递归或静默放行
            log.error("写路径数据范围校验被重入（解析自身查询调用了本方法），已按「拒绝」处理。"
                + "targetDeptId={}", deptId);
            return false;
        }
        long currentUserId = userId.get();
        ResolvedScope scope;
        RESOLVING.set(Boolean.TRUE);
        try {
            if (isSuperAdmin(currentUserId)) {
                return true;
            }
            scope = resolveScope(currentUserId);
        } finally {
            RESOLVING.remove();
        }
        if (scope.unlimited()) {
            return true;
        }
        // deptId 为空时不允许：读路径条件是 dept_id IN (...)，NULL 不匹配任何部门，写进去就再也读不到
        boolean within = deptId != null && scope.deptIds().contains(deptId);
        if (!within) {
            log.warn("写路径数据范围校验未通过：userId={}, targetDeptId={}, 可见部门={}",
                currentUserId, deptId, scope.deptIds());
        }
        return within;
    }

    /**
     * 加载用户全部角色的数据范围声明。
     *
     * <p>实现方负责把库里的 {@code data_scope} 码用 {@link DataScopeCalculator.ScopeType#fromCode(Integer)}
     * 解析后填入 {@link RoleScope}（脏值解析为 {@link DataScopeCalculator.ScopeType#UNKNOWN}），
     * 不要在实现里另写一套并集逻辑。返回空列表表示无角色（读/写路径都会拒绝全部）。</p>
     *
     * @param userId 用户 ID
     * @return 角色范围列表；无角色返回空列表（不得返回 {@code null}）
     */
    protected abstract List<RoleScope> loadRoleScopes(long userId);

    /**
     * 加载「父部门 ID → 子部门 ID 列表」索引（一次全量查询，避免逐层查库）。
     *
     * <p>仅在存在 {@link DataScopeCalculator.ScopeType#DEPT_AND_CHILD} /
     * {@link DataScopeCalculator.ScopeType#CUSTOM} 角色时才会被调用。</p>
     *
     * @return 部门索引；无数据返回空 Map（不得返回 {@code null}）
     */
    protected abstract Map<Long, List<Long>> loadDeptChildren();

}

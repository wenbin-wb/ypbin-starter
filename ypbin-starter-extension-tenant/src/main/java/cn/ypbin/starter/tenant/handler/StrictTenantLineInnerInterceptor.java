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
package cn.ypbin.starter.tenant.handler;

import cn.ypbin.starter.core.exception.BusinessException;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Values;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;
import org.jspecify.annotations.Nullable;

/**
 * 拒绝显式改写租户列的 UPDATE 的租户行拦截器。
 *
 * <p>MyBatis-Plus 的 {@link TenantLineInnerInterceptor} 只在 WHERE 上追加当前租户条件，
 * 不会拒绝 {@code UPDATE ... SET tenant_id = ?}：当前租户的行仍可被整体迁移到另一个租户。
 * 本类在父类改写前检查 SET 子句，命中租户列即抛 {@link BusinessException}（fail-closed）。
 * 合法的跨租户迁移必须显式使用 {@code @TenantIgnore} / {@code TenantContext.executeIgnore}。</p>
 *
 * <p><b>INSERT 侧（4.4.1 起）：</b>{@link DefaultTenantLineHandler#ignoreInsert(List, String)} 恒返回
 * {@code false}，插件总会再追加一次租户列——因此<strong>显式携带租户列的 INSERT 若与上下文不一致，
 * 数据库会因「同名列重复」直接拒绝</strong>。这条 fail-closed 能挡住「请求体绑定 {@code tenantId}
 * 写入任意租户」，但也会误伤「显式值本就等于上下文」的正常写入（4.4.0 的兼容性问题：宿主实体
 * 在 INSERT 前置了 {@code setTenantId(当前租户)}，升级后全部写入失败）。</p>
 *
 * <p>本类在父类改写前先行校验：显式租户列存在时，值必须可判定且<strong>等于当前上下文租户</strong>
 * ——相等则跳过追加（保持原 SQL，行为与 4.3.x 一致），不等或无法判定则抛 {@link BusinessException}
 * 并给出修复指引。跨租户写入仍走 {@code @TenantIgnore}/{@code TenantContext.executeIgnore}
 * （{@code ignoreTable} 命中时本校验不介入，显式值原样保留）。</p>
 *
 * <p><b>WHERE 子查询：</b>MyBatis-Plus 只在 SELECT 的 {@code PlainSelect} 里处理 WHERE 子查询，
 * {@code UPDATE}/{@code DELETE} 的 WHERE 直接交给租户条件拼接——{@code ... WHERE id IN
 * (SELECT id FROM other_table)} 里的子查询不会追加租户条件，等于让当前租户按其它租户的数据
 * 决定改/删哪些行（越权读 + 跨租户命中）。本类在父类改写前先对 WHERE 做一次子查询处理。</p>
 *
 * @author wenbin
 * @since 2026-11-06
 */
public class StrictTenantLineInnerInterceptor extends TenantLineInnerInterceptor {

    public StrictTenantLineInnerInterceptor(TenantLineHandler tenantLineHandler) {
        super(tenantLineHandler);
    }

    /**
     * INSERT 的租户列校验（见类注释）：显式值与上下文一致时跳过追加，不一致或无法判定即拒绝。
     *
     * @param insert 待改写语句
     * @param index  语句序号（父类约定）
     * @param sql    原始 SQL
     * @param obj    参数对象（MyBatis 传入的实体；用于读取显式租户值）
     */
    @Override
    protected void processInsert(Insert insert, int index, String sql, Object obj) {
        TenantLineHandler handler = getTenantLineHandler();
        if (handler.ignoreTable(insert.getTable().getName())) {
            // 忽略上下文/忽略表：不追加、不改写，显式租户值原样保留（跨租户写入的合法通道）
            super.processInsert(insert, index, sql, obj);
            return;
        }
        String tenantColumn = handler.getTenantIdColumn();
        int tenantIndex = indexOfColumn(insert.getColumns(), tenantColumn);
        if (tenantIndex < 0) {
            // 未显式携带租户列：交给父类追加，保持原有行为
            super.processInsert(insert, index, sql, obj);
            return;
        }

        Long contextTenant = currentTenant(handler);
        Long declaredTenant = declaredTenant(insert, tenantIndex, obj);
        if (contextTenant == null || declaredTenant == null || !contextTenant.equals(declaredTenant)) {
            throw new BusinessException("INSERT 显式携带的 " + tenantColumn + "=" + describe(declaredTenant)
                + " 与当前租户上下文 " + describe(contextTenant) + " 不一致或无法校验，已拒绝执行；"
                + "跨租户写入请使用 TenantContext.executeWithTenant / executeIgnore 包裹，"
                + "或移除实体上的显式租户值，交由租户插件按上下文写入");
        }
        // 显式值与上下文一致：保持原 SQL（父类若再追加会因同名列重复被数据库拒绝）
    }

    @Override
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        TenantLineHandler handler = getTenantLineHandler();
        if (!handler.ignoreTable(update.getTable().getName())) {
            String tenantIdColumn = handler.getTenantIdColumn();
            for (UpdateSet updateSet : update.getUpdateSets()) {
                if (updateSet.getColumns() == null) {
                    continue;
                }
                for (Column column : updateSet.getColumns()) {
                    if (tenantIdColumn.equalsIgnoreCase(column.getColumnName())) {
                        throw new BusinessException("禁止通过 UPDATE 修改租户列 " + tenantIdColumn
                            + "；跨租户迁移请显式使用 @TenantIgnore 或 TenantContext.executeIgnore");
                    }
                }
            }
        }
        // 父类只处理 SELECT 的 WHERE 子查询与 UPDATE 的 SET 值子查询，WHERE 里的子查询需在此补处理
        processWhereSubSelect(update.getWhere(), (String) obj);
        super.processUpdate(update, index, sql, obj);
    }

    @Override
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        // 同 processUpdate：DELETE 的 WHERE 子查询默认不会被追加租户条件
        processWhereSubSelect(delete.getWhere(), (String) obj);
        super.processDelete(delete, index, sql, obj);
    }

    private static int indexOfColumn(@Nullable List<Column> columns, String tenantColumn) {
        if (columns == null) {
            return -1;
        }
        for (int index = 0; index < columns.size(); index++) {
            if (tenantColumn.equalsIgnoreCase(columns.get(index).getColumnName())) {
                return index;
            }
        }
        return -1;
    }

    /**
     * 当前上下文租户（与 {@link DefaultTenantLineHandler#getTenantId()} 同源，含 fail-on-missing-tenant 拒绝）。
     *
     * @param handler 租户处理器
     * @return 租户 ID；无法确定时为 {@code null}
     */
    private static @Nullable Long currentTenant(TenantLineHandler handler) {
        Expression tenant = handler.getTenantId();
        return tenant instanceof LongValue longValue ? longValue.getValue() : null;
    }

    /**
     * 读取 INSERT 语句里显式租户列的值。
     *
     * <p>只接受单行 VALUES：字面量直接取值；占位符（MyBatis-Plus 由实体生成）则从参数对象上读
     * {@code tenantId}。多行 VALUES / INSERT..SELECT 无法用单个参数对象核对，返回 {@code null}
     * 由调用方按 fail-closed 拒绝。</p>
     *
     * @param insert         待改写语句
     * @param tenantIndex    租户列在列清单中的下标
     * @param parameterObject MyBatis 参数对象
     * @return 显式租户值；无法判定时为 {@code null}
     */
    private static @Nullable Long declaredTenant(Insert insert, int tenantIndex, @Nullable Object parameterObject) {
        // 注意：Insert#getValues() 在 INSERT..SELECT 上会直接把 Select 强转为 Values 并抛 ClassCastException，
        // 故先在 Select 上判定形态
        Values values = insert.getSelect() instanceof Values candidate ? candidate : null;
        if (values == null) {
            // INSERT ... SELECT：值来自子查询，无法静态核对
            return null;
        }
        List<Expression> row = singleRow(values);
        if (row == null || tenantIndex >= row.size()) {
            return null;
        }
        Expression value = row.get(tenantIndex);
        if (value instanceof LongValue longValue) {
            return longValue.getValue();
        }
        return entityTenantId(parameterObject);
    }

    private static @Nullable List<Expression> singleRow(Values values) {
        List<Expression> expressions = values.getExpressions();
        if (expressions == null || expressions.isEmpty()) {
            return null;
        }
        if (expressions.size() == 1 && expressions.get(0) instanceof ParenthesedExpressionList<?> row) {
            return List.copyOf(row);
        }
        for (Expression expression : expressions) {
            if (expression instanceof ParenthesedExpressionList<?>) {
                // 多行 VALUES：每行一个括号列表，无法用单个实体对象核对
                return null;
            }
        }
        return List.copyOf(expressions);
    }

    /**
     * 从 MyBatis 参数对象（实体本身或其 ParamMap 包装）读取 {@code tenantId}。
     *
     * @param parameterObject 参数对象
     * @return 实体上的租户 ID；读不到时为 {@code null}
     */
    private static @Nullable Long entityTenantId(@Nullable Object parameterObject) {
        Object candidate = parameterObject;
        if (candidate instanceof Map<?, ?> map) {
            candidate = map.containsKey("et") ? map.get("et") : map.get("param1");
        }
        if (candidate == null) {
            return null;
        }
        try {
            Method getter = candidate.getClass().getMethod("getTenantId");
            Object value = getter.invoke(candidate);
            return value instanceof Number number ? number.longValue() : null;
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private static String describe(@Nullable Long tenantId) {
        return tenantId == null ? "（无法校验）" : tenantId.toString();
    }
}

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
package cn.ypbin.starter.datapermission.handler;

import cn.ypbin.starter.datapermission.core.DataPermissionContext;
import cn.ypbin.starter.datapermission.core.DataScopeHandler;
import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 数据范围多表处理器适配。
 *
 * <p>将业务方的 {@link DataScopeHandler}（返回 SQL 片段）适配为 MyBatis-Plus 的
 * {@link MultiDataPermissionHandler}：把 SQL 片段解析为 JSqlParser 表达式追加到查询条件。
 * SQL 片段为空时返回 {@code null}，表示当前查询不做数据范围限制。</p>
 *
 * <p><b>为什么必须给片段补表限定名：</b>业务方通常返回 {@code dept_id IN (1,2)} 这样的裸列片段，
 * MyBatis-Plus 会把同一个片段同时拼到主表 WHERE 和每个 JOIN 的 ON 上。多表查询里裸列要么因
 * 多张表都有同名列而直接报 {@code ambiguous column}，要么被数据库解析到 JOIN 的另一张表上——
 * 前者暴露成故障，后者静默返回越权数据（更危险）。因此解析后统一把<b>未加限定</b>的列补成
 * 当前表的别名（无别名时用表名）；片段里已显式限定（{@code other.dept_id}）的列保持原样，
 * 业务确需跨表条件时仍可自行书写。表名与别名都无法推导时直接拒绝执行，不生成裸列。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class DataScopeMultiHandler implements MultiDataPermissionHandler {

    private static final Logger log = LoggerFactory.getLogger(DataScopeMultiHandler.class);

    private final DataScopeHandler dataScopeHandler;

    public DataScopeMultiHandler(DataScopeHandler dataScopeHandler) {
        this.dataScopeHandler = dataScopeHandler;
    }

    @Override
    @Nullable
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        // 仅在 @DataPermission 方法作用域内才拼接数据范围，避免全局无差别拦截
        if (!DataPermissionContext.isActive()) {
            return null;
        }
        String sql = dataScopeHandler.getDataScopeSql(mappedStatementId, table.getName());
        if (sql == null || sql.isBlank()) {
            return null;
        }
        Table qualifier = new Table(resolveQualifier(table));
        try {
            Expression expression = CCJSqlParserUtil.parseCondExpression(sql);
            expression.accept(new ColumnQualifier(qualifier));
            return expression;
        } catch (Exception e) {
            log.error("[ypbin-starter] 数据范围 SQL 片段解析失败: {}", sql, e);
            throw new IllegalArgumentException("数据范围 SQL 片段解析失败", e);
        }
    }

    /**
     * 推导当前表的限定名：优先别名（{@code select ... from sys_user u} 用 {@code u}），
     * 否则用表名；两者都为空时 fail-closed，绝不返回裸列片段。
     *
     * @param table MyBatis-Plus 传入的表
     * @return 可用于限定列名的表标识
     */
    private static String resolveQualifier(Table table) {
        if (table.getAlias() != null) {
            String alias = table.getAlias().getName();
            if (alias != null && !alias.isBlank()) {
                return alias;
            }
        }
        String name = table.getName();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                "无法推导数据范围片段的表限定名（表名与别名均为空），已拒绝执行以免生成裸列条件");
        }
        return name;
    }

    /**
     * 给未限定表名的列补上当前表限定名。
     *
     * <p>JSqlParser 的 {@link ExpressionVisitorAdapter} 默认会递归遍历二元表达式、IN、函数参数等节点，
     * 因此这里只需处理列节点本身；已带限定名的列（含子查询内的列）不做改动。</p>
     */
    private static final class ColumnQualifier extends ExpressionVisitorAdapter<Void> {

        private final Table qualifier;

        private ColumnQualifier(Table qualifier) {
            this.qualifier = qualifier;
        }

        @Override
        public <S> Void visit(Column column, S context) {
            Table owner = column.getTable();
            if (owner == null || owner.getName() == null || owner.getName().isBlank()) {
                column.setTable(qualifier);
            }
            return null;
        }
    }
}

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.ypbin.starter.core.exception.BusinessException;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import java.util.List;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.ExistsExpression;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.junit.jupiter.api.Test;

/**
 * {@link StrictTenantLineInnerInterceptor} 的 SQL 改写断言。
 *
 * <p>重点覆盖 MyBatis-Plus 父类未处理的场景：{@code UPDATE}/{@code DELETE} 的 WHERE 子查询
 * 是否会追加租户条件。</p>
 *
 * @author wenbin
 * @since 2026-11-06
 */
class StrictTenantLineInnerInterceptorTest {

    private static final String TENANT_COLUMN = "tenant_id";

    private final ExposedInterceptor interceptor = new ExposedInterceptor(new StubTenantLineHandler());

    @Test
    void updateShouldAppendTenantConditionInsideWhereSubSelect() throws Exception {
        Update update = (Update) CCJSqlParserUtil.parse(
            "UPDATE sys_order SET status = 1 WHERE user_id IN (SELECT id FROM sys_user WHERE status = 1)");

        interceptor.rewriteUpdate(update);

        AndExpression outer = (AndExpression) update.getWhere();
        assertThat(outer.getRightExpression()).hasToString(TENANT_COLUMN + " = 1");
        // 子查询内部同样要追加租户条件，否则会按其它租户的数据决定改哪些行
        assertThat(subSelectWhere(outer.getLeftExpression()).toString()).isEqualToIgnoringWhitespace("status = 1 AND tenant_id = 1");
    }

    @Test
    void deleteShouldAppendTenantConditionInsideWhereSubSelect() throws Exception {
        Delete delete = (Delete) CCJSqlParserUtil.parse(
            "DELETE FROM sys_order WHERE user_id IN (SELECT id FROM sys_user WHERE status = 1)");

        interceptor.rewriteDelete(delete);

        AndExpression outer = (AndExpression) delete.getWhere();
        assertThat(outer.getRightExpression()).hasToString(TENANT_COLUMN + " = 1");
        assertThat(subSelectWhere(outer.getLeftExpression()).toString()).isEqualToIgnoringWhitespace("status = 1 AND tenant_id = 1");
    }

    @Test
    void deleteShouldAppendTenantConditionInsideExistsSubSelect() throws Exception {
        Delete delete = (Delete) CCJSqlParserUtil.parse(
            "DELETE FROM sys_order WHERE EXISTS (SELECT 1 FROM sys_user WHERE status = 1)");

        interceptor.rewriteDelete(delete);

        AndExpression outer = (AndExpression) delete.getWhere();
        assertThat(subSelectWhere(outer.getLeftExpression()).toString()).isEqualToIgnoringWhitespace("status = 1 AND tenant_id = 1");
    }

    @Test
    void updateShouldRejectExplicitTenantColumnAssignment() throws Exception {
        Update update = (Update) CCJSqlParserUtil.parse(
            "UPDATE sys_order SET tenant_id = 2 WHERE id = 1");

        assertThatThrownBy(() -> interceptor.rewriteUpdate(update))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("禁止通过 UPDATE 修改租户列");
    }

    private static Expression subSelectWhere(Expression expression) {
        Expression right = expression instanceof InExpression inExpression
            ? inExpression.getRightExpression()
            : ((ExistsExpression) expression).getRightExpression();
        Select select = (Select) right;
        PlainSelect plainSelect = select instanceof ParenthesedSelect parenthesedSelect
            ? parenthesedSelect.getPlainSelect()
            : (PlainSelect) select;
        return plainSelect.getWhere();
    }

    /**
     * 暴露父类 protected 的改写入口，便于直接对解析后的语句做断言。
     */
    private static final class ExposedInterceptor extends StrictTenantLineInnerInterceptor {

        private ExposedInterceptor(TenantLineHandler tenantLineHandler) {
            super(tenantLineHandler);
        }

        private void rewriteUpdate(Update update) {
            processUpdate(update, 0, update.toString(), null);
        }

        private void rewriteDelete(Delete delete) {
            processDelete(delete, 0, delete.toString(), null);
        }
    }

    private static final class StubTenantLineHandler implements TenantLineHandler {

        @Override
        public Expression getTenantId() {
            return new LongValue(1);
        }

        @Override
        public String getTenantIdColumn() {
            return TENANT_COLUMN;
        }

        @Override
        public boolean ignoreTable(String tableName) {
            return false;
        }

        @Override
        public boolean ignoreInsert(List<Column> columns, String tenantIdColumn) {
            return true;
        }
    }
}

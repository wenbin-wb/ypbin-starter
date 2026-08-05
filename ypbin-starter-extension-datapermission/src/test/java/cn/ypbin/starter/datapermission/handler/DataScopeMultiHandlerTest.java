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

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.datapermission.core.DataPermissionContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link DataScopeMultiHandler} 单元测试。
 *
 * @author wenbin
 * @since 2026-08-05
 */
class DataScopeMultiHandlerTest {

    private final Table table = new Table("sys_order");

    @AfterEach
    void tearDown() {
        // 防止某个用例进入作用域后未清理，污染同线程后续用例
        while (DataPermissionContext.isActive()) {
            DataPermissionContext.exit();
        }
    }

    @Test
    void shouldReturnParsedExpressionWhenActiveAndSqlPresent() {
        DataScopeMultiHandler handler = new DataScopeMultiHandler((mappedStatementId, tableName) -> "dept_id IN (1,2,3)");
        DataPermissionContext.enter();

        Expression expression = handler.getSqlSegment(table, null, "com.demo.OrderMapper.selectList");

        assertThat(expression).isNotNull();
        assertThat(expression.toString()).isEqualToIgnoringWhitespace("dept_id IN (1, 2, 3)");
    }

    @Test
    void shouldReturnNullWhenContextInactive() {
        DataScopeMultiHandler handler = new DataScopeMultiHandler((mappedStatementId, tableName) -> "dept_id IN (1,2,3)");

        Expression expression = handler.getSqlSegment(table, null, "com.demo.OrderMapper.selectList");

        assertThat(expression).isNull();
    }

    @Test
    void shouldReturnNullWhenSqlIsNull() {
        DataScopeMultiHandler handler = new DataScopeMultiHandler((mappedStatementId, tableName) -> null);
        DataPermissionContext.enter();

        Expression expression = handler.getSqlSegment(table, null, "com.demo.OrderMapper.selectList");

        assertThat(expression).isNull();
    }

    @Test
    void shouldReturnNullWhenSqlIsBlank() {
        DataScopeMultiHandler handler = new DataScopeMultiHandler((mappedStatementId, tableName) -> "   ");
        DataPermissionContext.enter();

        Expression expression = handler.getSqlSegment(table, null, "com.demo.OrderMapper.selectList");

        assertThat(expression).isNull();
    }

    @Test
    void shouldReturnNullWhenSqlUnparsable() {
        DataScopeMultiHandler handler = new DataScopeMultiHandler((mappedStatementId, tableName) -> "this is not valid sql !!!");
        DataPermissionContext.enter();

        Expression expression = handler.getSqlSegment(table, null, "com.demo.OrderMapper.selectList");

        assertThat(expression).isNull();
    }

    @Test
    void shouldPassMappedStatementIdAndTableNameToHandler() {
        String[] captured = new String[2];
        DataScopeMultiHandler handler = new DataScopeMultiHandler((mappedStatementId, tableName) -> {
            captured[0] = mappedStatementId;
            captured[1] = tableName;
            return "dept_id = 1";
        });
        DataPermissionContext.enter();

        handler.getSqlSegment(table, null, "com.demo.OrderMapper.selectList");

        assertThat(captured[0]).isEqualTo("com.demo.OrderMapper.selectList");
        assertThat(captured[1]).isEqualTo("sys_order");
    }

    @Test
    void shouldStayActiveUntilNestedScopesAllExit() {
        DataScopeMultiHandler handler = new DataScopeMultiHandler((mappedStatementId, tableName) -> "dept_id = 1");
        DataPermissionContext.enter();
        DataPermissionContext.enter();

        DataPermissionContext.exit();
        // 外层作用域仍在，拦截器应继续拼接
        assertThat(handler.getSqlSegment(table, null, "com.demo.OrderMapper.selectList")).isNotNull();

        DataPermissionContext.exit();
        // 全部退出后不再拦截
        assertThat(handler.getSqlSegment(table, null, "com.demo.OrderMapper.selectList")).isNull();
    }
}

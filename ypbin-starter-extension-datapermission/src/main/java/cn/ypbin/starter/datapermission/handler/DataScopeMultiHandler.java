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
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 数据范围多表处理器适配。
 *
 * <p>将业务方的 {@link DataScopeHandler}（返回 SQL 片段）适配为 MyBatis-Plus 的
 * {@link MultiDataPermissionHandler}：把 SQL 片段解析为 JSqlParser 表达式追加到查询条件。
 * SQL 片段为空时返回 {@code null}，表示当前查询不做数据范围限制。</p>
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
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        // 仅在 @DataPermission 方法作用域内才拼接数据范围，避免全局无差别拦截
        if (!DataPermissionContext.isActive()) {
            return null;
        }
        String sql = dataScopeHandler.getDataScopeSql(mappedStatementId, table.getName());
        if (sql == null || sql.isBlank()) {
            return null;
        }
        try {
            return CCJSqlParserUtil.parseCondExpression(sql);
        } catch (Exception e) {
            log.error("[ypbin-starter] 数据范围 SQL 片段解析失败: {}", sql, e);
            throw new IllegalArgumentException("数据范围 SQL 片段解析失败", e);
        }
    }
}

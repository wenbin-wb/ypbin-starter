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
import java.util.List;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.statement.update.UpdateSet;

/**
 * 拒绝显式改写租户列的 UPDATE 的租户行拦截器。
 *
 * <p>MyBatis-Plus 的 {@link TenantLineInnerInterceptor} 只在 WHERE 上追加当前租户条件，
 * 不会拒绝 {@code UPDATE ... SET tenant_id = ?}：当前租户的行仍可被整体迁移到另一个租户。
 * 本类在父类改写前检查 SET 子句，命中租户列即抛 {@link BusinessException}（fail-closed）。
 * 合法的跨租户迁移必须显式使用 {@code @TenantIgnore} / {@code TenantContext.executeIgnore}。</p>
 *
 * <p>INSERT 侧由 {@link DefaultTenantLineHandler#ignoreInsert(List, String)} 恒返回 {@code false}
 * 兜底：显式写租户列的 INSERT / INSERT..SELECT 会被插件追加重复列并由数据库拒绝。</p>
 *
 * @author wenbin
 * @since 2026-11-06
 */
public class StrictTenantLineInnerInterceptor extends TenantLineInnerInterceptor {

    public StrictTenantLineInnerInterceptor(TenantLineHandler tenantLineHandler) {
        super(tenantLineHandler);
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
        super.processUpdate(update, index, sql, obj);
    }
}

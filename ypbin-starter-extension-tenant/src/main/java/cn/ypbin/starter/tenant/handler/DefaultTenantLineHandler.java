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

import cn.ypbin.starter.tenant.autoconfigure.TenantProperties;
import cn.ypbin.starter.tenant.core.TenantContext;
import cn.ypbin.starter.tenant.core.TenantProvider;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import java.util.Set;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;

/**
 * 默认租户行处理器。
 *
 * <p>为 SQL 自动追加租户条件。租户值取自 {@link TenantProvider}，无租户上下文时返回
 * {@code NULL}（不产生越权数据）；配置的忽略表跳过隔离。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class DefaultTenantLineHandler implements TenantLineHandler {

    private final TenantProvider tenantProvider;
    private final TenantProperties properties;
    private final Set<String> ignoreTables;

    public DefaultTenantLineHandler(TenantProvider tenantProvider, TenantProperties properties) {
        this.tenantProvider = tenantProvider;
        this.properties = properties;
        this.ignoreTables = Set.copyOf(properties.getIgnoreTables());
    }

    @Override
    public Expression getTenantId() {
        return tenantProvider.getCurrentTenantId()
            .<Expression>map(LongValue::new)
            .orElseGet(NullValue::new);
    }

    @Override
    public String getTenantIdColumn() {
        return properties.getColumn();
    }

    @Override
    public boolean ignoreTable(String tableName) {
        // 线程级忽略（@TenantIgnore / TenantContext）优先，其次是配置的静态忽略表
        return TenantContext.isIgnored() || ignoreTables.contains(tableName);
    }
}

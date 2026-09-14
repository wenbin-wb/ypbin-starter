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
import cn.ypbin.starter.tenant.autoconfigure.TenantProperties;
import cn.ypbin.starter.tenant.core.TenantContext;
import cn.ypbin.starter.tenant.core.TenantProvider;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import java.util.Optional;
import java.util.Set;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.jspecify.annotations.Nullable;

/**
 * 默认租户行处理器。
 *
 * <p>为 SQL 自动追加租户条件。优先使用 {@link TenantContext} 显式绑定的租户，其次读取
 * {@link TenantProvider}；两者都无租户时按 {@code ypbin.tenant.fail-on-missing-tenant}
 * （默认 true，fail-closed）决定拒绝还是跳过隔离，配置的忽略表始终跳过隔离。</p>
 *
 * <p><strong>不可返回 {@code NullValue}：</strong>MyBatis-Plus 仅在 {@link #getTenantId()} 返回
 * Java {@code null} 时跳过追加条件；返回 {@code NullValue} 会拼出 {@code tenant_id = NULL}，
 * 导致查询恒为空、写入 NULL 租户后无法再查出。</p>
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
    @Nullable
    public Expression getTenantId() {
        Optional<Long> tenantId = TenantContext.getTenantId()
            .or(tenantProvider::getCurrentTenantId);
        if (tenantId.isPresent()) {
            return new LongValue(tenantId.get());
        }
        if (properties.isFailOnMissingTenant()) {
            throw new BusinessException("缺少租户上下文，已拒绝执行跨租户查询；"
                + "如需跨租户操作请显式使用 @TenantIgnore 或 TenantContext.executeIgnore");
        }
        // 返回 Java null 让 MyBatis-Plus 跳过追加租户条件（切勿返回 NullValue）
        return null;
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

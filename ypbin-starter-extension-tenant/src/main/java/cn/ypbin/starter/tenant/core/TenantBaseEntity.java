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
package cn.ypbin.starter.tenant.core;

import cn.ypbin.starter.data.core.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import java.io.Serial;

/**
 * 带租户字段的实体基类。
 *
 * <p>在 {@link BaseEntity} 基础上追加 {@code tenantId}，供多租户业务实体继承。租户隔离本身由
 * MyBatis-Plus 的租户行拦截器自动在 SQL 上追加条件（见 tenant 模块），本字段用于实体层读写租户值、
 * 以及插入时由 {@code tenant_id} 列承载。列名默认 {@code tenant_id}，与 {@code ypbin.tenant.column} 对齐；
 * 若自定义了该配置，请同步用 {@code @TableField} 覆盖列名。</p>
 *
 * <p>不需要租户隔离的实体继承 {@link BaseEntity} 即可，避免基础实体被迫携带 tenant_id 列。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
public abstract class TenantBaseEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户 ID。插入时由租户行拦截器自动注入 SQL 条件，本字段供实体层读写租户值 */
    @TableField("tenant_id")
    private Long tenantId;

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }
}

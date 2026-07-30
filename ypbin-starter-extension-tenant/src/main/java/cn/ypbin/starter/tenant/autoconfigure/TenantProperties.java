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
package cn.ypbin.starter.tenant.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 多租户配置项。
 *
 * @author wenbin
 * @since 2026-07-30
 */
@ConfigurationProperties(prefix = "ypbin.tenant")
public class TenantProperties {

    /** 是否启用多租户，默认关闭（需显式开启） */
    private boolean enabled = false;

    /** 租户字段列名 */
    private String column = "tenant_id";

    /** 忽略租户隔离的表（这些表不追加租户条件） */
    private List<String> ignoreTables = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public List<String> getIgnoreTables() {
        return ignoreTables;
    }

    public void setIgnoreTables(List<String> ignoreTables) {
        this.ignoreTables = ignoreTables;
    }
}

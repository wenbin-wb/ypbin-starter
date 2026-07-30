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
package cn.ypbin.starter.data.autoconfigure;

import com.baomidou.mybatisplus.annotation.DbType;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据模块配置项。
 *
 * @author wenbin
 * @since 2026-07-30
 */
@ConfigurationProperties(prefix = "ypbin.data")
public class DataProperties {

    /** 是否启用数据增强，默认开启 */
    private boolean enabled = true;

    /** 数据库类型，用于分页方言，默认 MySQL */
    private DbType dbType = DbType.MYSQL;

    /** 单页最大条数限制，防止恶意超大分页，-1 表示不限制 */
    private long maxLimit = 500L;

    /** 溢出总页数后是否进行处理（跳回首页） */
    private boolean overflow = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public DbType getDbType() {
        return dbType;
    }

    public void setDbType(DbType dbType) {
        this.dbType = dbType;
    }

    public long getMaxLimit() {
        return maxLimit;
    }

    public void setMaxLimit(long maxLimit) {
        this.maxLimit = maxLimit;
    }

    public boolean isOverflow() {
        return overflow;
    }

    public void setOverflow(boolean overflow) {
        this.overflow = overflow;
    }
}

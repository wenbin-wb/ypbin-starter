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
package cn.ypbin.starter.crud.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分页查询参数。
 *
 * <p>通用分页入参，页码从 1 开始。可作为业务查询条件对象的父类，附加过滤字段。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class PageQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 页码（从 1 开始） */
    private long page = 1L;

    /** 每页条数 */
    private long pageSize = 10L;

    /** 排序字段 */
    private String sortField;

    /** 是否升序 */
    private boolean asc = true;

    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getPageSize() {
        return pageSize;
    }

    public void setPageSize(long pageSize) {
        this.pageSize = pageSize;
    }

    public String getSortField() {
        return sortField;
    }

    public void setSortField(String sortField) {
        this.sortField = sortField;
    }

    public boolean isAsc() {
        return asc;
    }

    public void setAsc(boolean asc) {
        this.asc = asc;
    }
}

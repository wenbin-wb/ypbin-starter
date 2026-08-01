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
import java.util.Collections;
import java.util.List;

/**
 * 分页查询结果。
 *
 * @param <T> 记录类型
 * @author wenbin
 * @since 2026-07-30
 */
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前页数据 */
    private List<T> items;

    /** 总记录数 */
    private long total;

    /** 当前页码 */
    private long page;

    /** 每页条数 */
    private long pageSize;

    public PageResult() {
        this.items = Collections.emptyList();
    }

    public PageResult(List<T> items, long total, long page, long pageSize) {
        this.items = (items != null) ? items : Collections.emptyList();
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

    /**
     * 构造分页结果。
     *
     * @param items    当前页数据
     * @param total    总数
     * @param page     页码
     * @param pageSize 每页条数
     * @param <T>      记录类型
     * @return 分页结果
     */
    public static <T> PageResult<T> of(List<T> items, long total, long page, long pageSize) {
        return new PageResult<>(items, total, page, pageSize);
    }

    /**
     * 总页数。
     *
     * @return 总页数
     */
    public long getPages() {
        return (pageSize <= 0) ? 0 : (total + pageSize - 1) / pageSize;
    }

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

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
}

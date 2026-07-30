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
package cn.ypbin.starter.crud.service;

import cn.ypbin.starter.crud.model.PageQuery;
import cn.ypbin.starter.crud.model.PageResult;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import java.util.regex.Pattern;

/**
 * 通用业务服务实现。
 *
 * <p>继承 MyBatis-Plus 的 {@link ServiceImpl} 复用其成熟能力，仅额外实现分页方法：
 * 将框架的 {@link PageQuery}/{@link PageResult} 适配为 MyBatis-Plus 分页对象。
 * 业务服务实现类继承本类即获得开箱即用的 CRUD 与分页。</p>
 *
 * @param <M> Mapper 类型
 * @param <T> 实体类型
 * @author wenbin
 * @since 2026-07-30
 */
public class BaseServiceImpl<M extends BaseMapper<T>, T>
    extends ServiceImpl<M, T> implements BaseService<T> {

    /**
     * 合法排序字段名白名单模式：仅允许字母、数字、下划线。
     *
     * <p>排序字段来自 HTTP 请求，直接拼入 ORDER BY 会导致 SQL 盲注（MyBatis-Plus 对自定义
     * OrderItem 的列名不转义）。故对字段名做严格白名单校验，非法值直接忽略排序。</p>
     */
    private static final Pattern SAFE_COLUMN = Pattern.compile("^[a-zA-Z0-9_]+$");

    @Override
    public PageResult<T> page(PageQuery query) {
        Page<T> page = new Page<>(query.getPage(), query.getSize());
        String sortField = query.getSortField();
        if (sortField != null && !sortField.isBlank()) {
            if (!SAFE_COLUMN.matcher(sortField).matches()) {
                throw new IllegalArgumentException("非法排序字段：" + sortField);
            }
            page.addOrder(query.isAsc() ? OrderItem.asc(sortField) : OrderItem.desc(sortField));
        }
        IPage<T> result = super.page(page);
        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }
}

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
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * 通用业务服务契约。
 *
 * <p>继承 MyBatis-Plus 的 {@link IService} 复用其成熟的增删改查方法（save / updateById /
 * removeById / getById / list 等），仅额外定义与框架 {@link PageResult} 对接的分页方法，
 * 避免重复声明造成的方法签名冲突。业务服务接口继承本接口即可。</p>
 *
 * @param <T> 实体类型
 * @author wenbin
 * @since 2026-07-30
 */
public interface BaseService<T> extends IService<T> {

    /**
     * 分页查询（无业务过滤）。
     *
     * @param query 分页参数
     * @return 分页结果
     */
    PageResult<T> page(PageQuery query);

    /**
     * 分页查询（带业务过滤条件）。
     *
     * @param query   分页参数
     * @param wrapper 查询条件，为 {@code null} 时等价于无条件分页
     * @return 分页结果
     */
    PageResult<T> page(PageQuery query, Wrapper<T> wrapper);
}

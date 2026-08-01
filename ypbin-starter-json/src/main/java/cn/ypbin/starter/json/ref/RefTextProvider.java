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
package cn.ypbin.starter.json.ref;

import java.util.Collection;
import java.util.Map;

/**
 * 引用翻译数据来源扩展点。
 *
 * <p>把引用 ID（如 createUser、deptId）翻译为展示名称。starter 只定义查询契约，数据源（用户表、部门表等）
 * 由业务系统实现。</p>
 *
 * <p><b>刻意设计为批量接口</b>：一次传入一组 ID、一次返回 {@code id -> name} 映射，从根源规避列表翻译时的
 * N+1 查询。业务实现应用 {@code WHERE id IN (...)} 一次查完，而非逐个查。{@link RefTextManager} 会在此之上
 * 叠加缓存与预加载，进一步减少回源。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public interface RefTextProvider {

    /**
     * 支持的引用类型标识（如 {@code "user"}、{@code "dept"}），与 {@code @RefText(type=...)} 对应。
     *
     * @return 类型标识
     */
    String type();

    /**
     * 批量查询 ID 对应的展示名称。
     *
     * @param ids 引用 ID 集合（已去重、非空）
     * @return {@code id -> name} 映射；查不到的 ID 可不出现在结果中
     */
    Map<Object, String> getNames(Collection<Object> ids);
}

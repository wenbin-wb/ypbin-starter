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
package cn.ypbin.starter.core.tree;

import java.util.List;

/**
 * 树节点契约。
 *
 * <p>菜单、部门、分类等树形数据的节点实现本接口后，即可用 {@link TreeUtils} 一键把扁平列表
 * 组装为树。{@code ID} 为节点标识类型（Long、String 等）。</p>
 *
 * @param <T>  节点自身类型
 * @param <ID> 标识类型
 * @author wenbin
 * @since 2026-07-30
 */
public interface TreeNode<T, ID> {

    /**
     * 节点 ID。
     *
     * @return 节点 ID
     */
    ID getId();

    /**
     * 父节点 ID。
     *
     * @return 父节点 ID，根节点通常为 {@code null} 或指定的根标识
     */
    ID getParentId();

    /**
     * 设置子节点列表。
     *
     * @param children 子节点
     */
    void setChildren(List<T> children);
}

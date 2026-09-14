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
package cn.ypbin.starter.benchmarks;

import cn.ypbin.starter.core.tree.TreeNode;
import java.util.List;

/**
 * 基准测试用的树节点（{@link TreeNode} 的最小实现）。
 *
 * @author wenbin
 * @since 2026-09-14
 */
public class BenchNode implements TreeNode<BenchNode, Long> {

    private final Long id;

    private final Long parentId;

    private List<BenchNode> children = List.of();

    /**
     * 构造节点。
     *
     * @param id       节点标识
     * @param parentId 父节点标识（根传 {@code null}）
     */
    public BenchNode(Long id, Long parentId) {
        this.id = id;
        this.parentId = parentId;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public Long getParentId() {
        return parentId;
    }

    @Override
    public List<BenchNode> getChildren() {
        return children;
    }

    @Override
    public void setChildren(List<BenchNode> children) {
        this.children = children;
    }
}

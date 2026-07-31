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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 树形结构构建工具。
 *
 * <p>把扁平列表按父子关系组装为树。采用父 ID 索引，整体 O(n)，避免逐节点递归查找的 O(n²)。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public final class TreeUtils {

    private TreeUtils() {
    }

    /**
     * 构建树：以「父 ID 为空或不在列表中」的节点为根。
     *
     * @param nodes 扁平节点列表
     * @param <T>   节点类型
     * @param <ID>  标识类型
     * @return 根节点列表（每个根的 children 已递归填充）
     */
    public static <T extends TreeNode<T, ID>, ID> List<T> build(List<T> nodes) {
        return build(nodes, null);
    }

    /**
     * 构建树：以指定 rootParentId 的节点为根。
     *
     * @param nodes        扁平节点列表
     * @param rootParentId 根节点的父 ID（如 0 或 null）
     * @param <T>          节点类型
     * @param <ID>         标识类型
     * @return 根节点列表
     */
    public static <T extends TreeNode<T, ID>, ID> List<T> build(List<T> nodes, ID rootParentId) {
        if (nodes == null || nodes.isEmpty()) {
            return new ArrayList<>();
        }
        // 父 ID -> 子节点列表 索引，一次遍历建立
        Map<ID, List<T>> childrenIndex = nodes.stream()
            .filter(n -> n.getParentId() != null)
            .collect(Collectors.groupingBy(TreeNode::getParentId));

        List<T> roots = new ArrayList<>();
        for (T node : nodes) {
            node.setChildren(childrenIndex.getOrDefault(node.getId(), new ArrayList<>()));
            if (isRoot(node, rootParentId, nodes)) {
                roots.add(node);
            }
        }
        return roots;
    }

    /**
     * 过滤树（保留匹配节点及其祖先链）。先构建再按条件裁剪。
     *
     * @param nodes     扁平节点列表
     * @param predicate 保留条件
     * @param <T>       节点类型
     * @param <ID>      标识类型
     * @return 过滤后的根节点列表
     */
    public static <T extends TreeNode<T, ID>, ID> List<T> buildAndFilter(List<T> nodes, Predicate<T> predicate) {
        if (nodes == null || nodes.isEmpty()) {
            return new ArrayList<>();
        }
        List<T> matched = nodes.stream().filter(predicate).collect(Collectors.toList());
        return build(matched);
    }

    /**
     * 把树（含 children）展平为一维列表（深度优先，先序）。
     *
     * @param roots 根节点列表
     * @param <T>   节点类型
     * @param <ID>  标识类型
     * @return 展平后的全部节点
     */
    public static <T extends TreeNode<T, ID>, ID> List<T> flatten(List<T> roots) {
        List<T> result = new ArrayList<>();
        if (roots == null || roots.isEmpty()) {
            return result;
        }
        Deque<T> stack = new ArrayDeque<>();
        // 逆序入栈保证先序输出
        for (int i = roots.size() - 1; i >= 0; i--) {
            stack.push(roots.get(i));
        }
        while (!stack.isEmpty()) {
            T node = stack.pop();
            result.add(node);
            List<T> children = node.getChildren();
            if (children != null) {
                for (int i = children.size() - 1; i >= 0; i--) {
                    stack.push(children.get(i));
                }
            }
        }
        return result;
    }

    /**
     * 收集指定节点的全部子孙 ID（不含自身）。常用于删除子树、数据权限下钻等。
     *
     * @param node 起始节点（其 children 需已填充）
     * @param <T>  节点类型
     * @param <ID> 标识类型
     * @return 全部子孙 ID
     */
    public static <T extends TreeNode<T, ID>, ID> List<ID> getDescendantIds(T node) {
        List<ID> ids = new ArrayList<>();
        if (node == null || node.getChildren() == null) {
            return ids;
        }
        for (T child : node.getChildren()) {
            ids.add(child.getId());
            ids.addAll(getDescendantIds(child));
        }
        return ids;
    }

    /**
     * 在树中按 ID 查找节点（深度优先）。
     *
     * @param roots 根节点列表
     * @param id    目标 ID
     * @param <T>   节点类型
     * @param <ID>  标识类型
     * @return 匹配节点，未找到为 {@code null}
     */
    public static <T extends TreeNode<T, ID>, ID> T findNode(List<T> roots, ID id) {
        for (T node : flatten(roots)) {
            if (Objects.equals(node.getId(), id)) {
                return node;
            }
        }
        return null;
    }

    private static <T extends TreeNode<T, ID>, ID> boolean isRoot(T node, ID rootParentId, List<T> nodes) {
        ID parentId = node.getParentId();
        if (rootParentId != null) {
            return Objects.equals(parentId, rootParentId);
        }
        // 未指定根标识：父 ID 为空，或父节点不在列表中，视为根
        if (parentId == null) {
            return true;
        }
        return nodes.stream().noneMatch(n -> Objects.equals(n.getId(), parentId));
    }
}

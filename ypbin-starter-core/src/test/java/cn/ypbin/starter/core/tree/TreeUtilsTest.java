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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/**
 * {@link TreeUtils} 单元测试。
 *
 * @author wenbin
 * @since 2026-07-30
 */
class TreeUtilsTest {

    /**
     * 统计 {@code getId()} 调用次数的节点：把复杂度从「挂钟耗时」变成「确定性计数」。
     *
     * <p>实现若对每个节点线性扫描父节点，调用次数会按 n² 增长；守卫测试据此断言线性。</p>
     */
    static class CountingNode implements TreeNode<CountingNode, Long> {

        static final AtomicLong GET_ID_CALLS = new AtomicLong();

        final Long id;
        final Long parentId;
        List<CountingNode> children = List.of();

        CountingNode(Long id, Long parentId) {
            this.id = id;
            this.parentId = parentId;
        }

        @Override
        public Long getId() {
            GET_ID_CALLS.incrementAndGet();
            return id;
        }

        @Override
        public Long getParentId() {
            return parentId;
        }

        @Override
        public void setChildren(List<CountingNode> children) {
            this.children = children;
        }

        @Override
        public List<CountingNode> getChildren() {
            return children;
        }
    }

    /**
     * 最坏形态：全部子节点在前、父节点在列表末尾（每个子节点都要扫到最后才找到父节点）。
     */
    private static long getIdCallsForWorstCase(int size) {
        List<CountingNode> nodes = new ArrayList<>(size + 1);
        for (int i = 1; i <= size; i++) {
            // 子节点 id 与父节点 id 不撞号（避免「恰好提前命中」掩盖线性扫描）
            nodes.add(new CountingNode(1_000_000L + i, 0L));
        }
        nodes.add(new CountingNode(0L, null));

        CountingNode.GET_ID_CALLS.set(0);
        List<CountingNode> roots = TreeUtils.build(nodes);
        assertThat(roots).hasSize(1);
        return CountingNode.GET_ID_CALLS.get();
    }

    @Test
    void buildShouldBeLinearInNodeCount() {
        // 当前实现为「ID 集合一次 + setChildren 一次」≈ 2n 次 getId；阈值取 6n：
        // 既给实现留 3 倍常数空间，又与 O(n²)（n=4000 时约 n·n ≈ 1600 万次）相差 600 倍以上。
        assertThat(getIdCallsForWorstCase(4_000)).isLessThan(6L * 4_000);
        assertThat(getIdCallsForWorstCase(16_000)).isLessThan(6L * 16_000);
    }

    static class Node implements TreeNode<Node, Long> {
        final Long id;
        final Long parentId;
        List<Node> children;

        Node(Long id, Long parentId) {
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
        public void setChildren(List<Node> children) {
            this.children = children;
        }

        @Override
        public List<Node> getChildren() {
            return children;
        }
    }

    @Test
    void build_shouldAssembleTree_byNullParent() {
        List<Node> flat = new ArrayList<>();
        flat.add(new Node(1L, null));
        flat.add(new Node(2L, 1L));
        flat.add(new Node(3L, 1L));
        flat.add(new Node(4L, 2L));

        List<Node> roots = TreeUtils.build(flat);

        assertThat(roots).hasSize(1);
        Node root = roots.getFirst();
        assertThat(root.id).isEqualTo(1L);
        assertThat(root.children).hasSize(2);
        Node child2 = root.children.stream().filter(n -> n.id == 2L).findFirst().orElseThrow();
        assertThat(child2.children).hasSize(1);
        assertThat(child2.children.getFirst().id).isEqualTo(4L);
    }

    @Test
    void build_shouldTreatOrphanAsRoot_whenParentNotInList() {
        List<Node> flat = new ArrayList<>();
        flat.add(new Node(10L, 999L)); // 父不在列表中
        flat.add(new Node(11L, 10L));

        List<Node> roots = TreeUtils.build(flat);

        assertThat(roots).hasSize(1);
        assertThat(roots.getFirst().id).isEqualTo(10L);
        assertThat(roots.getFirst().children).hasSize(1);
    }

    @Test
    void build_withExplicitRootParentId() {
        List<Node> flat = new ArrayList<>();
        flat.add(new Node(1L, 0L));
        flat.add(new Node(2L, 1L));

        List<Node> roots = TreeUtils.build(flat, 0L);

        assertThat(roots).hasSize(1);
        assertThat(roots.getFirst().id).isEqualTo(1L);
    }

    @Test
    void build_emptyOrNull_shouldReturnEmpty() {
        List<Node> empty = new ArrayList<>();
        assertThat(TreeUtils.build(empty)).isEmpty();
    }

    @Test
    void flatten_shouldReturnAllNodesInPreorder() {
        List<Node> flat = new ArrayList<>();
        flat.add(new Node(1L, null));
        flat.add(new Node(2L, 1L));
        flat.add(new Node(3L, 1L));
        flat.add(new Node(4L, 2L));

        List<Node> all = TreeUtils.flatten(TreeUtils.build(flat));

        assertThat(all).extracting(n -> n.id).containsExactly(1L, 2L, 4L, 3L);
    }

    @Test
    void getDescendantIds_shouldCollectAllDescendants() {
        List<Node> flat = new ArrayList<>();
        flat.add(new Node(1L, null));
        flat.add(new Node(2L, 1L));
        flat.add(new Node(3L, 1L));
        flat.add(new Node(4L, 2L));

        List<Node> roots = TreeUtils.build(flat);

        assertThat(TreeUtils.getDescendantIds(roots.getFirst())).containsExactlyInAnyOrder(2L, 3L, 4L);
    }

    @Test
    void findNode_shouldLocateByIdOrReturnNull() {
        List<Node> flat = new ArrayList<>();
        flat.add(new Node(1L, null));
        flat.add(new Node(2L, 1L));

        List<Node> roots = TreeUtils.build(flat);

        assertThat(TreeUtils.findNode(roots, 2L)).isNotNull();
        assertThat(TreeUtils.findNode(roots, 999L)).isNull();
    }
}

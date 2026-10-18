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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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

    // ── 环防护（issue #107 验收 3）──────────────────────────────────────────

    /**
     * 成环数据：1←2←3←1（3 的父是 2、2 的父是 1、1 的父是 3）。
     *
     * <p>递归实现会一直往下钻直至 {@link StackOverflowError}；迭代 + visited 必须正常返回。</p>
     */
    @Test
    void getDescendantIds_shouldNotOverflowOnCyclicChildren() {
        Node node1 = new Node(1L, 3L);
        Node node2 = new Node(2L, 1L);
        Node node3 = new Node(3L, 2L);
        // 手工构造成环的 children 图：1 -> 2 -> 3 -> 1
        node1.setChildren(new ArrayList<>(List.of(node2)));
        node2.setChildren(new ArrayList<>(List.of(node3)));
        node3.setChildren(new ArrayList<>(List.of(node1)));

        // 旧实现（递归、无 visited）在此抛 StackOverflowError
        List<Long> descendantIds = TreeUtils.getDescendantIds(node1);

        // 起始节点自身不计入子孙；环回到 1 时因已访问被跳过
        assertThat(descendantIds).containsExactly(2L, 3L);
    }

    /** 自环（父是自己）同样不得栈溢出，且不得把自身当子孙。 */
    @Test
    void getDescendantIds_shouldSkipSelfLoop() {
        Node self = new Node(7L, 7L);
        self.setChildren(new ArrayList<>(List.of(self)));

        assertThat(TreeUtils.getDescendantIds(self)).isEmpty();
    }

    /** 同一子节点被多个父挂载（脏数据里的「多父」）也不得重复输出。 */
    @Test
    void getDescendantIds_shouldDeduplicateSharedChildren() {
        Node root = new Node(1L, null);
        Node shared = new Node(2L, 1L);
        Node other = new Node(3L, 1L);
        root.setChildren(new ArrayList<>(List.of(shared, other)));
        shared.setChildren(new ArrayList<>(List.of(other)));
        other.setChildren(new ArrayList<>());

        assertThat(TreeUtils.getDescendantIds(root)).containsExactly(2L, 3L);
    }

    // ── 函数式重载（issue #106 ①）──────────────────────────────────────────

    /** 字段名与 {@link TreeNode} 不一致的宿主实体：只有 id / pid，没有 getParentId()。 */
    static class Row {
        final Long id;
        final Long pid;
        List<Row> children = new ArrayList<>();

        Row(Long id, Long pid) {
            this.id = id;
            this.pid = pid;
        }

        void setChildren(List<Row> children) {
            this.children = children;
        }
    }

    private static List<Row> sampleRows() {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(1L, null));
        rows.add(new Row(2L, 1L));
        rows.add(new Row(3L, 1L));
        rows.add(new Row(4L, 2L));
        rows.add(new Row(5L, 999L)); // 父不在列表：自动视为根
        return rows;
    }

    @Test
    void functionalBuild_shouldProduceSameTreeAsInterfaceBuild() {
        List<Node> flat = new ArrayList<>();
        flat.add(new Node(1L, null));
        flat.add(new Node(2L, 1L));
        flat.add(new Node(3L, 1L));
        flat.add(new Node(4L, 2L));
        flat.add(new Node(5L, 999L));

        List<Node> interfaceTree = TreeUtils.build(flat);
        List<Row> functionalTree = TreeUtils.build(sampleRows(), row -> row.id, row -> row.pid, Row::setChildren);

        assertThat(flattenRowIds(functionalTree)).isEqualTo(flattenNodeIds(interfaceTree));
        assertThat(functionalTree).extracting(row -> row.id).containsExactly(1L, 5L);
        assertThat(functionalTree.getFirst().children).extracting(row -> row.id).containsExactly(2L, 3L);
        assertThat(functionalTree.getFirst().children.getFirst().children)
            .extracting(row -> row.id).containsExactly(4L);
    }

    @Test
    void functionalBuild_withExplicitRootParentId() {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(1L, 0L));
        rows.add(new Row(2L, 1L));
        rows.add(new Row(3L, 0L));

        List<Row> roots = TreeUtils.build(rows, row -> row.id, row -> row.pid, Row::setChildren, 0L);

        assertThat(roots).extracting(row -> row.id).containsExactly(1L, 3L);
        assertThat(roots.getFirst().children).extracting(row -> row.id).containsExactly(2L);
    }

    @Test
    void functionalBuild_emptyNullAndInvalidArgs() {
        assertThat(TreeUtils.build(new ArrayList<Row>(), row -> row.id, row -> row.pid, Row::setChildren)).isEmpty();
        assertThat(TreeUtils.build((List<Row>) null, row -> row.id, row -> row.pid, Row::setChildren)).isEmpty();
        assertThatThrownBy(() -> TreeUtils.build(sampleRows(), null, row -> row.pid, Row::setChildren))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TreeUtils.build(sampleRows(), row -> row.id, null, Row::setChildren))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TreeUtils.build(sampleRows(), row -> row.id, row -> row.pid, null))
            .isInstanceOf(NullPointerException.class);
    }

    /** 函数式重载同样必须是 O(n)：getId 调用次数随节点数线性增长。 */
    @Test
    void functionalBuild_shouldBeLinearInNodeCount() {
        List<CountingNode> nodes = new ArrayList<>();
        for (int i = 1; i <= 4_000; i++) {
            nodes.add(new CountingNode(1_000_000L + i, 0L));
        }
        nodes.add(new CountingNode(0L, null));
        CountingNode.GET_ID_CALLS.set(0);

        List<CountingNode> roots = TreeUtils.build(nodes, node -> node.getId(), node -> node.parentId,
            (node, children) -> node.children = children);

        assertThat(roots).hasSize(1);
        assertThat(CountingNode.GET_ID_CALLS.get()).isLessThan(6L * 4_000);
    }

    private static List<Long> flattenNodeIds(List<Node> roots) {
        return TreeUtils.flatten(roots).stream().map(node -> node.id).toList();
    }

    private static List<Long> flattenRowIds(List<Row> roots) {
        List<Long> ids = new ArrayList<>();
        Deque<Row> stack = new ArrayDeque<>(roots);
        while (!stack.isEmpty()) {
            Row row = stack.pop();
            ids.add(row.id);
            for (int i = row.children.size() - 1; i >= 0; i--) {
                stack.push(row.children.get(i));
            }
        }
        return ids;
    }
}

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

import cn.ypbin.starter.core.tree.TreeUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * 树组装基准：验证 {@link TreeUtils#build(List)} 的 O(n) 声明。
 *
 * <p><strong>为什么要同时测「父在前」与「父在后」两种形态</strong>：根判定需要回答「父 ID 是否在列表内」，
 * 若实现按逐节点线性扫描，则「父节点排在列表末尾」时每个子节点都要扫到最后才命中，整体退化为 O(n²)；
 * 而「父节点在前」会让扫描提前命中，测出来仍像线性——<strong>只测一种形态会掩盖退化</strong>。</p>
 *
 * <p>该形态确实踩过：早期基准用 {@code rootId * 1_000_000 + child} 造 ID，子 ID 与根 ID 撞号，
 * 使最坏形态被掩盖，并据此得出了错误的「线性」结论（真实实现当时是 O(n²)，已修复）。</p>
 *
 * <p>两种形态的 ID 都刻意不撞号；规模按 {@code size} 放大：线性实现下两者每节点成本应接近，
 * 且规模放大 10 倍耗时约 10 倍。</p>
 *
 * @author wenbin
 * @since 2026-09-14
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class TreeUtilsBenchmark {

    private static final int ROOTS = 10;

    /** 扁平节点总数（10 个根 + 其余子节点） */
    @Param({"1000", "10000"})
    private int size;

    /** 常见形态：根在前、其后是各根的子节点 */
    private List<BenchNode> parentsFirst;

    /** 最坏形态：全部子节点在前、根节点在最后（若实现线性扫描父节点，此处必然退化） */
    private List<BenchNode> childrenFirst;

    @Setup
    public void prepare() {
        parentsFirst = new ArrayList<>(size);
        childrenFirst = new ArrayList<>(size);
        int perRoot = Math.max(1, size / ROOTS);
        for (int root = 0; root < ROOTS; root++) {
            long rootId = root + 1L;
            BenchNode rootNode = new BenchNode(rootId, null);
            parentsFirst.add(rootNode);
            for (int child = 1; child < perRoot; child++) {
                // 子 ID 与根 ID 严格不撞号，避免「恰好提前命中」掩盖线性扫描
                BenchNode childNode = new BenchNode(1_000_000L + rootId * 100_000L + child, rootId);
                parentsFirst.add(childNode);
                childrenFirst.add(childNode);
            }
            childrenFirst.add(rootNode);
        }
    }

    @Benchmark
    public List<BenchNode> buildParentsFirst() {
        return TreeUtils.build(parentsFirst);
    }

    @Benchmark
    public List<BenchNode> buildChildrenFirst() {
        return TreeUtils.build(childrenFirst);
    }
}

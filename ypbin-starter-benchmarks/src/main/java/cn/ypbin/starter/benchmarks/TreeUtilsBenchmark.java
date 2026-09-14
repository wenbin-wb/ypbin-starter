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
 * <p>节点规模按 {@code size} 参数放大，观察耗时是否线性增长——若退化为 O(n²)（例如在循环里线性查找父节点），
 * 耗时增长会明显快于规模增长。</p>
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

    /** 扁平节点总数（10 个根 + 每根若干子节点） */
    @Param({"1000", "10000"})
    private int size;

    private List<BenchNode> flat;

    @Setup
    public void prepare() {
        flat = new ArrayList<>(size);
        int roots = 10;
        int perRoot = size / roots;
        for (int root = 0; root < roots; root++) {
            long rootId = root;
            flat.add(new BenchNode(rootId, null));
            for (int child = 1; child < perRoot; child++) {
                flat.add(new BenchNode(rootId * 1_000_000L + child, rootId));
            }
        }
    }

    @Benchmark
    public List<BenchNode> build() {
        return TreeUtils.build(flat);
    }
}

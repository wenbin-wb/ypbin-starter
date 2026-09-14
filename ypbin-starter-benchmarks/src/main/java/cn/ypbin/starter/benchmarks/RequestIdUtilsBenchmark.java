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

import cn.ypbin.starter.core.util.RequestIdUtils;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * 链路 ID 基准：{@link RequestIdUtils} 是每个请求都会走的热路径（校验客户端传入值 + 必要时生成）。
 *
 * <p>重点看三档成本：合法值（只做长度与可见 ASCII 校验）、非法值（提前返回）、生成（随机数 + 编码）。
 * 该路径若被写出逐字符的对象分配或正则，耗时会明显抬升。</p>
 *
 * @author wenbin
 * @since 2026-09-14
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class RequestIdUtilsBenchmark {

    private String valid;

    private String withControlChar;

    private String tooLong;

    @Setup
    public void prepare() {
        valid = "0af7651916cd43dd8448eb211c80319c";
        // 含控制字符（模拟日志注入尝试，应立即被拒）
        withControlChar = "0af76519\r\nX-Injected: 1";
        tooLong = "a".repeat(200);
    }

    @Benchmark
    public String sanitizeValid() {
        return RequestIdUtils.sanitize(valid);
    }

    @Benchmark
    public String sanitizeControlChar() {
        return RequestIdUtils.sanitize(withControlChar);
    }

    @Benchmark
    public String sanitizeTooLong() {
        return RequestIdUtils.sanitize(tooLong);
    }

    @Benchmark
    public String generate() {
        return RequestIdUtils.generate();
    }
}

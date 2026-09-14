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

import cn.ypbin.starter.cache.redis.RedisJsonSerializerFactory;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * 缓存值序列化基准：写路径（含不可变集合规范化）与读路径（多态类型还原）。
 *
 * <p>用默认 {@code JsonMapper} 作为基底（{@code create(null)}），只度量序列化器自身与规范化开销，
 * 不把容器级 Jackson 定制掺进来；容器定制对序列化器是固定成本。</p>
 *
 * @author wenbin
 * @since 2026-09-14
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class RedisSerializerBenchmark {

    private RedisSerializer<Object> serializer;

    private CachePayload payload;

    private byte[] serialized;

    @Setup(Level.Trial)
    public void prepare() {
        serializer = RedisJsonSerializerFactory.create(null);
        CachePayload prepared = new CachePayload();
        prepared.id = 1L;
        prepared.name = "基准载荷";
        prepared.tags = List.of("a", "b", "c", "d", "e");
        prepared.attrs = Map.of("k1", "v1", "k2", 2, "k3", true);
        prepared.nested = List.of(List.of("x", "y"), Map.of("inner", List.of(1, 2, 3)));
        payload = prepared;
        serialized = serializer.serialize(prepared);
    }

    @Benchmark
    public byte[] serialize() {
        return serializer.serialize(payload);
    }

    @Benchmark
    public Object deserialize() {
        return serializer.deserialize(serialized);
    }
}

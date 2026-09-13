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
package cn.ypbin.starter.cache.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * {@link ImmutableCollectionNormalizer} 单元测试。
 *
 * <p>锁定「不可变集合 → 可变集合」的规范化语义：这是多态类型信息能写入的前提，
 * 真实 Redis 往返由 {@code RedisJacksonJsonRoundTripIT} 覆盖。</p>
 *
 * @author wenbin
 * @since 2026-09-13
 */
@SuppressWarnings("unchecked")
class ImmutableCollectionNormalizerTest {

    @Test
    void immutableListShouldBecomeArrayList() {
        Object normalized = ImmutableCollectionNormalizer.normalize(List.of("a", "b"));

        assertThat(normalized).isInstanceOf(ArrayList.class);
        assertThat((List<Object>) normalized).containsExactly("a", "b");
    }

    @Test
    void streamToListResultShouldBecomeArrayList() {
        Object normalized = ImmutableCollectionNormalizer.normalize(Stream.of("a").toList());

        assertThat(normalized).isInstanceOf(ArrayList.class);
    }

    @Test
    void immutableSetShouldBecomeLinkedHashSet() {
        Object normalized = ImmutableCollectionNormalizer.normalize(Set.of("a"));

        assertThat(normalized).isInstanceOf(LinkedHashSet.class);
        assertThat((Set<Object>) normalized).containsExactly("a");
    }

    @Test
    void immutableMapShouldBecomeLinkedHashMap() {
        Object normalized = ImmutableCollectionNormalizer.normalize(Map.of("k", "v"));

        assertThat(normalized).isInstanceOf(LinkedHashMap.class);
        assertThat((Map<Object, Object>) normalized).containsEntry("k", "v");
    }

    @Test
    void mutableCollectionsShouldAlsoBeCopied() {
        // 统一替换为规范实现，避免依赖传入的具体实现类型
        assertThat(ImmutableCollectionNormalizer.normalize(new ArrayList<>(List.of("a"))))
            .isInstanceOf(ArrayList.class);
        assertThat(ImmutableCollectionNormalizer.normalize(new HashMap<>(Map.of("k", "v"))))
            .isInstanceOf(LinkedHashMap.class);
    }

    @Test
    void nestedImmutableCollectionsShouldBeNormalized() {
        Object normalized = ImmutableCollectionNormalizer.normalize(
            List.of(List.of("a"), Map.of("k", List.of("b"))));

        assertThat(normalized).isInstanceOf(ArrayList.class);
        List<Object> outer = (List<Object>) normalized;
        assertThat((Object) outer.get(0)).isInstanceOf(ArrayList.class);
        Map<Object, Object> nestedMap = (Map<Object, Object>) outer.get(1);
        assertThat(nestedMap).isInstanceOf(LinkedHashMap.class);
        assertThat((Object) nestedMap.get("k")).isInstanceOf(ArrayList.class);
    }

    @Test
    void emptyCollectionsShouldBeNormalized() {
        assertThat(ImmutableCollectionNormalizer.normalize(List.of())).isInstanceOf(ArrayList.class);
        assertThat(ImmutableCollectionNormalizer.normalize(Map.of())).isInstanceOf(LinkedHashMap.class);
    }

    @Test
    void plainObjectAndNullShouldPassThrough() {
        Object pojo = new Object();
        assertThat(ImmutableCollectionNormalizer.normalize(pojo)).isSameAs(pojo);
        assertThat(ImmutableCollectionNormalizer.normalize(null)).isNull();
        assertThat(ImmutableCollectionNormalizer.normalize("text")).isEqualTo("text");
        assertThat(ImmutableCollectionNormalizer.normalize(42)).isEqualTo(42);
    }

    @Test
    void selfReferencingCollectionShouldNotLoopForever() {
        List<Object> selfReferencing = new ArrayList<>();
        selfReferencing.add(selfReferencing);

        Object normalized = ImmutableCollectionNormalizer.normalize(selfReferencing);

        assertThat(normalized).isInstanceOf(ArrayList.class);
    }
}

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 不可变集合规范化器：把 JDK 不可变集合转换为可变实现，使多态类型信息可被正常写入。
 *
 * <p><strong>为什么需要：</strong>Redis 值序列化依赖 Jackson 多态类型信息（{@code @class} / 类型包装数组）
 * 才能在读回时还原具体类型。而 JDK 不可变集合（{@code List.of()}、{@code Map.of()}、
 * {@code Set.of()}、{@code Stream.toList()} 的产物，以及 {@code Collections.unmodifiableXxx}）
 * 是 <em>final</em> 类型：{@code As.PROPERTY} 形态无法向其写入类型属性，序列化器会**静默丢弃类型标识**，
 * 反序列化时随即因「缺少类型 id」直接失败。实测 {@code GenericJacksonJsonRedisSerializer}
 * （Spring Data Redis 4.1 / Jackson 3）对可变集合（ArrayList/HashMap）正常、对不可变集合必失败。</p>
 *
 * <p>本规范化器只做「集合 → 可变集合」的浅层与嵌套替换，不触碰 POJO：POJO 字段按声明类型序列化，
 * 不依赖多态标识；被替换的集合元素仍按各自类型继续处理。集合规范性为语义等价，
 * 业务侧读回的是可变集合而非原来的不可变实现，不影响使用。</p>
 *
 * @author wenbin
 * @since 2026-09-13
 */
final class ImmutableCollectionNormalizer {

    /** 递归深度上限，防御异常深的对象图 */
    private static final int MAX_DEPTH = 16;

    private ImmutableCollectionNormalizer() {
    }

    /**
     * 规范化入口：集合类→可变集合，其它类型原样返回。
     *
     * @param value 原始值
     * @return 规范化后的值
     */
    static Object normalize(Object value) {
        return normalize(value, new IdentityHashMap<>(), 0);
    }

    private static Object normalize(Object value, IdentityHashMap<Object, Object> seen, int depth) {
        if (value == null || depth > MAX_DEPTH) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            return normalizeMap(map, seen, depth);
        }
        if (value instanceof Set<?> set) {
            return normalizeSet(set, seen, depth);
        }
        if (value instanceof List<?> list) {
            return normalizeList(list, seen, depth);
        }
        if (value instanceof Collection<?> collection) {
            // 其它集合实现（Deque 等）统一降级为 ArrayList，保证可变且可写入类型标识
            LinkedHashSet<Object> copy = new LinkedHashSet<>(collection.size());
            return normalizeInto(collection, copy, seen, depth);
        }
        // POJO：字段按声明类型序列化，无需在此深挖
        return value;
    }

    private static Object normalizeMap(Map<?, ?> map, IdentityHashMap<Object, Object> seen, int depth) {
        if (seen.put(map, Boolean.TRUE) != null) {
            return map;
        }
        Map<Object, Object> copy = new LinkedHashMap<>(Math.max(4, map.size() * 2));
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            copy.put(normalize(entry.getKey(), seen, depth + 1),
                normalize(entry.getValue(), seen, depth + 1));
        }
        return copy;
    }

    private static Object normalizeSet(Set<?> set, IdentityHashMap<Object, Object> seen, int depth) {
        if (seen.put(set, Boolean.TRUE) != null) {
            return set;
        }
        LinkedHashSet<Object> copy = new LinkedHashSet<>(Math.max(4, set.size() * 2));
        return normalizeInto(set, copy, seen, depth);
    }

    private static Object normalizeList(List<?> list, IdentityHashMap<Object, Object> seen, int depth) {
        if (seen.put(list, Boolean.TRUE) != null) {
            return list;
        }
        List<Object> copy = new ArrayList<>(list.size());
        for (Object element : list) {
            copy.add(normalize(element, seen, depth + 1));
        }
        return copy;
    }

    private static Object normalizeInto(Collection<?> source, Collection<Object> target,
            IdentityHashMap<Object, Object> seen, int depth) {
        for (Object element : source) {
            target.add(normalize(element, seen, depth + 1));
        }
        return target;
    }
}

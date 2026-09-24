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
package cn.ypbin.starter.tools.idempotent;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 方法入参的<strong>值指纹</strong>：为「内容相同」的两次调用生成相同字符串。
 *
 * <p>背景：{@code Arrays.deepHashCode(args)} 只对数组元素逐个调用 {@code hashCode()}，而 Req/Resp DTO 按本仓
 * 规范一律 {@code @Getter @Setter}（不带 {@code equals}/{@code hashCode}），于是两次**内容完全相同**的请求会得到
 * 不同的键，幂等形同虚设。本类改为按**字段值**展开：</p>
 *
 * <ul>
 *     <li>字符串/数字/布尔/枚举/时间/URI/UUID/File 等 JDK 叶子类型：直接用其 {@code toString()}；</li>
 *     <li>数组、{@link Collection}、{@link Map}、{@link Optional}：递归展开（{@code List} 保序，
 *     其它集合与 {@code Map} 的条目排序，保证跨实例稳定）；</li>
 *     <li>业务对象：按字段名排序后逐字段展开（含父类字段，跳过 {@code static}/{@code transient}/合成字段）。</li>
 * </ul>
 *
 * <p><strong>已知边界（刻意显式而不是静默）</strong>：递归深度上限 {@value #MAX_DEPTH} 层、循环引用与无法反射读取
 * 的字段都会在指纹里留下可见标记（{@value #TRUNCATED}/{@value #CYCLE}/{@code inaccessible}），不会被当成正常值；
 * 需要精确控制幂等维度时，用 {@link Idempotent#key()} 的 SpEL 显式指定（如带上业务单号或当前用户）。</p>
 *
 * @author wenbin
 * @since 2026-09-24
 */
final class ArgumentFingerprint {

    private static final Logger log = LoggerFactory.getLogger(ArgumentFingerprint.class);

    /** 递归深度上限：超过后只保留类型名，避免大对象图的指纹计算拖慢写入路径。 */
    private static final int MAX_DEPTH = 4;

    /** {@code null} 参数的占位。 */
    private static final String NULL = "null";

    /** 循环引用占位（同一对象在自身展开链上再次出现）。 */
    private static final String CYCLE = "!cycle";

    /** 深度截断占位。 */
    private static final String TRUNCATED = "!truncated";

    /** 字段不可读占位前缀。 */
    private static final String INACCESSIBLE = "!inaccessible:";

    private ArgumentFingerprint() {
    }

    /**
     * 生成全部入参的值指纹。
     *
     * @param args 方法入参，可为 {@code null}
     * @return 指纹字符串，永不为 {@code null}；不同参数以 {@code &} 分隔
     */
    static String of(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                builder.append('&');
            }
            builder.append(describe(args[i], 0, new IdentityHashMap<>()));
        }
        return builder.toString();
    }

    private static String describe(Object value, int depth, IdentityHashMap<Object, Boolean> visited) {
        if (value == null) {
            return NULL;
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            return array(value, depth, visited);
        }
        if (value instanceof Optional<?> optional) {
            return optional.map(item -> describe(item, depth + 1, visited)).orElse("empty");
        }
        if (value instanceof Collection<?> collection) {
            return collection(collection, depth, visited);
        }
        if (value instanceof Map<?, ?> map) {
            return map(map, depth, visited);
        }
        if (isLeaf(type)) {
            return type.getSimpleName() + '(' + value + ')';
        }
        if (depth >= MAX_DEPTH) {
            return type.getSimpleName() + TRUNCATED;
        }
        if (visited.put(value, Boolean.TRUE) != null) {
            return type.getSimpleName() + CYCLE;
        }
        try {
            return type.getSimpleName() + '{' + fields(value, type, depth, visited) + '}';
        } finally {
            visited.remove(value);
        }
    }

    private static String array(Object array, int depth, IdentityHashMap<Object, Boolean> visited) {
        int length = Array.getLength(array);
        List<String> items = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            items.add(describe(Array.get(array, i), depth + 1, visited));
        }
        return array.getClass().getSimpleName() + '[' + String.join(",", items) + ']';
    }

    private static String collection(Collection<?> collection, int depth,
        IdentityHashMap<Object, Boolean> visited) {
        if (depth >= MAX_DEPTH) {
            return collection.getClass().getSimpleName() + TRUNCATED;
        }
        if (visited.put(collection, Boolean.TRUE) != null) {
            return collection.getClass().getSimpleName() + CYCLE;
        }
        try {
            List<String> items = new ArrayList<>(collection.size());
            for (Object item : collection) {
                items.add(describe(item, depth + 1, visited));
            }
            if (!(collection instanceof List<?>)) {
                // 非 List 集合（Set 等）无序：排序后才能保证「跨实例、同内容」得到同一个指纹
                items.sort(Comparator.naturalOrder());
            }
            return '[' + String.join(",", items) + ']';
        } finally {
            visited.remove(collection);
        }
    }

    private static String map(Map<?, ?> map, int depth, IdentityHashMap<Object, Boolean> visited) {
        if (depth >= MAX_DEPTH) {
            return map.getClass().getSimpleName() + TRUNCATED;
        }
        if (visited.put(map, Boolean.TRUE) != null) {
            return map.getClass().getSimpleName() + CYCLE;
        }
        try {
            List<String> entries = new ArrayList<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                entries.add(describe(entry.getKey(), depth + 1, visited)
                    + "->" + describe(entry.getValue(), depth + 1, visited));
            }
            entries.sort(Comparator.naturalOrder());
            return '{' + String.join(",", entries) + '}';
        } finally {
            visited.remove(map);
        }
    }

    private static String fields(Object value, Class<?> type, int depth,
        IdentityHashMap<Object, Boolean> visited) {
        List<Field> declared = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class;
                current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()) {
                    continue;
                }
                declared.add(field);
            }
        }
        declared.sort(Comparator.comparing(Field::getName));
        StringJoiner joiner = new StringJoiner(",");
        for (Field field : declared) {
            joiner.add(field.getName() + '=' + read(field, value, depth, visited));
        }
        return joiner.toString();
    }

    private static String read(Field field, Object target, int depth,
        IdentityHashMap<Object, Boolean> visited) {
        try {
            if (!field.canAccess(target)) {
                field.setAccessible(true);
            }
            return describe(field.get(target), depth + 1, visited);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // 不因个别字段不可读而让整个写端点失效（幂等键生成失败会直接拒绝所有请求）；
            // 但绝不静默：占位符进入指纹，同时留 debug 日志说明是哪个类的哪个字段
            log.debug("[ypbin-starter] 幂等键生成跳过不可读字段 {}.{}",
                target.getClass().getName(), field.getName(), e);
            return INACCESSIBLE + field.getName();
        }
    }

    /**
     * 是否为「直接用 toString 表示」的叶子类型。
     *
     * <p>除包装/字符串/枚举外，其余 {@code java.*}/{@code javax.*}/{@code jdk.*}/{@code sun.*} 类型也按叶子处理：
     * 这些 JDK 类型的 {@code toString()} 是稳定的（时间、URI、UUID、File 等），而反射它们又常因模块封装失败。</p>
     */
    private static boolean isLeaf(Class<?> type) {
        if (type.isPrimitive() || type.isEnum()
            || CharSequence.class.isAssignableFrom(type)
            || Number.class.isAssignableFrom(type)
            || Boolean.class == type || Character.class == type) {
            return true;
        }
        String name = type.getName();
        return name.startsWith("java.") || name.startsWith("javax.")
            || name.startsWith("jdk.") || name.startsWith("sun.");
    }
}

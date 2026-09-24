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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 方法入参的<strong>值指纹摘要</strong>：为「内容相同」的两次调用生成相同字符串。
 *
 * <p>背景：{@code Arrays.deepHashCode(args)} 只对数组元素逐个调用 {@code hashCode()}，而 Req/Resp DTO 按本仓
 * 规范一律 {@code @Getter @Setter}（不带 {@code equals}/{@code hashCode}），于是两次**内容完全相同**的请求会得到
 * 不同的键，幂等形同虚设。本类改为按**字段值**展开后再取摘要：</p>
 *
 * <ul>
 *     <li>字符串/数字/布尔/枚举/时间/URI/UUID/File 等 JDK 叶子类型：直接用其 {@code toString()}；</li>
 *     <li>数组：{@code 类型名[元素...]}；{@link List} → {@code list[元素...]}（保序）；
 *     {@link Set} → {@code set[元素...]}（排序）；其它 {@link Collection} → {@code collection[元素...]}（排序）；
 *     {@link Map} → {@code map{k->v,...}}（条目排序）；{@link Optional} → {@code opt(值)} / {@code empty}；</li>
 *     <li>业务对象：{@code 全限定类名{字段=值,...}}，字段按字段名排序（含父类字段，跳过
 *     {@code static}/{@code transient}/合成字段）。</li>
 * </ul>
 *
 * <p><strong>为什么返回摘要而不是明文</strong>：幂等键会被写入存储（如 Redis 键空间）并在失败路径进日志，
 * 而参数里可能有口令、身份证号等敏感值。这里把展开后的值指纹做 SHA-256 并截取前
 * {@value #DIGEST_HEX_LENGTH} 个十六进制字符（128 位）后返回：既保持「内容相同 ⇒ 键相同」的语义，
 * 又让键长有界且不落明文。展开过程的中间串**不会**离开本类、不会进日志或存储。</p>
 *
 * <p><strong>已知边界（刻意显式而不是静默）</strong>：本类只保证「内容相同 ⇒ 指纹相同」，
 * <strong>不保证「内容不同 ⇒ 指纹不同」</strong>——以下情形会得到同一指纹，宿主需据此判断是否改用
 * {@link Idempotent#key()} 的 SpEL 精确指定幂等维度：</p>
 * <ul>
 *     <li>递归深度超过 {@value #MAX_DEPTH} 层的对象图，超出部分只保留类型名标记 {@value #TRUNCATED}；</li>
 *     <li>被跳过或无法读取的字段：{@code transient}/合成/静态字段不参与，反射不可读的字段记为
 *     {@value #INACCESSIBLE}（并记 debug 日志）；</li>
 *     <li>叶子类型的 {@code toString()} 本身不区分语义差异（例如两个不同的 {@code File} 指向同一路径的写法、
 *     或自定义 JDK 类型未覆盖 {@code toString}）。</li>
 * </ul>
 * <p>循环引用会在指纹里留下 {@value #CYCLE} 标记，不会栈溢出。</p>
 *
 * @author wenbin
 * @since 2026-09-24
 */
final class ArgumentFingerprint {

    private static final Logger log = LoggerFactory.getLogger(ArgumentFingerprint.class);

    /** 递归深度上限：超过后只保留类型名，避免大对象图的指纹计算拖慢写入路径。 */
    private static final int MAX_DEPTH = 4;

    /** 摘要字符数（SHA-256 十六进制前 32 位 = 128 bit）：碰撞概率可忽略，同时让键长有界。 */
    private static final int DIGEST_HEX_LENGTH = 32;

    /** 摘要算法。 */
    private static final String DIGEST_ALGORITHM = "SHA-256";

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
     * 生成全部入参的值指纹摘要。
     *
     * @param args 方法入参，可为 {@code null}
     * @return 十六进制摘要（{@value #DIGEST_HEX_LENGTH} 个字符），永不为 {@code null}；
     *     内容相同即相同，不含任何入参明文
     */
    static String of(Object[] args) {
        return digest(describe(args));
    }

    /**
     * 展开全部入参为可读的值指纹（仅供本包内测试与调试使用，**绝不**写入存储或日志）。
     *
     * @param args 方法入参，可为 {@code null}
     * @return 明文指纹；不同参数以 {@code &} 分隔
     */
    static String describe(Object[] args) {
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
            return optional.map(item -> "opt(" + describe(item, depth + 1, visited) + ')').orElse("empty");
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
            return type.getName() + TRUNCATED;
        }
        if (visited.put(value, Boolean.TRUE) != null) {
            return type.getName() + CYCLE;
        }
        try {
            return type.getName() + '{' + fields(value, type, depth, visited) + '}';
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
        // 容器语义类型前缀：List 保序、Set/其它集合排序；两者内容相同时不会得到同一指纹
        String kind = collection instanceof List<?> ? "list"
            : collection instanceof Set<?> ? "set" : "collection";
        if (depth >= MAX_DEPTH) {
            return kind + TRUNCATED;
        }
        if (visited.put(collection, Boolean.TRUE) != null) {
            return kind + CYCLE;
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
            return kind + '[' + String.join(",", items) + ']';
        } finally {
            visited.remove(collection);
        }
    }

    private static String map(Map<?, ?> map, int depth, IdentityHashMap<Object, Boolean> visited) {
        if (depth >= MAX_DEPTH) {
            return "map" + TRUNCATED;
        }
        if (visited.put(map, Boolean.TRUE) != null) {
            return "map" + CYCLE;
        }
        try {
            List<String> entries = new ArrayList<>(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                entries.add(describe(entry.getKey(), depth + 1, visited)
                    + "->" + describe(entry.getValue(), depth + 1, visited));
            }
            entries.sort(Comparator.naturalOrder());
            return "map{" + String.join(",", entries) + '}';
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
     * 对值指纹取 SHA-256 摘要并截断为固定长度十六进制串。
     */
    private static String digest(String fingerprint) {
        try {
            byte[] hash = MessageDigest.getInstance(DIGEST_ALGORITHM)
                .digest(fingerprint.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, DIGEST_HEX_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            // JDK 必然提供 SHA-256；真出现即环境损坏，不能静默退化成「不哈希」（那会把明文写进存储）
            throw new IllegalStateException("JVM 缺少 " + DIGEST_ALGORITHM + " 实现，无法生成幂等键摘要", e);
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

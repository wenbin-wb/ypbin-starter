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
package cn.ypbin.starter.json.ref;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 引用翻译预加载解析器。
 *
 * <p>列表/分页数据序列化前调用 {@link #preload(Object)}：反射遍历对象图，收集所有 {@link RefText} 字段的 ID，
 * <b>按引用类型分组，每类型合并成一次批量查询</b>回填缓存。之后序列化时 {@link RefTextSerializer} 全部命中缓存，
 * 彻底消除逐行 N+1 回源。</p>
 *
 * <p>典型用法（Controller 返回列表前）：</p>
 * <pre>{@code
 * List<OrderResp> list = orderService.list();
 * refTextResolver.preload(list);   // 一次性批量翻译，之后序列化零回源
 * return R.ok(list);
 * }</pre>
 *
 * <p>安全措施：{@link IdentityHashMap} 记录已访问对象防循环引用；限制递归深度；字段元数据按类缓存避免重复反射。
 * 只遍历本项目可能承载业务对象的结构（自定义 Bean、集合、Map、数组），跳过 JDK 内置类型。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class RefTextResolver {

    /** 递归最大深度，防御异常深的对象图 */
    private static final int MAX_DEPTH = 8;

    /** 类 -> 带 @RefText 的字段列表（含类型），按类缓存反射结果 */
    private static final Map<Class<?>, Field[]> REF_FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Field[] EMPTY = new Field[0];

    /** 元素类 -> 是否（含嵌套地）存在 @RefText 字段，供自动拦截快速跳过无关响应 */
    private static final Map<Class<?>, Boolean> CONTAINS_CACHE = new ConcurrentHashMap<>();

    private final RefTextManager manager;

    public RefTextResolver(RefTextManager manager) {
        this.manager = manager;
    }

    /**
     * 判断某类是否（含嵌套地）存在 {@link RefText} 字段。结果按类缓存，用于自动预加载时快速跳过
     * 不含引用字段的响应类型，避免无谓的对象图遍历。
     *
     * @param type 元素类型
     * @return 是否存在 @RefText 字段
     */
    public boolean containsRefText(Class<?> type) {
        if (type == null || isLeafType(type)) {
            return false;
        }
        return containsRefText(type, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    }

    private boolean containsRefText(Class<?> type, Set<Class<?>> seen, int depth) {
        if (type == null || isLeafType(type) || depth > MAX_DEPTH || !seen.add(type)) {
            return false;
        }
        Boolean cached = CONTAINS_CACHE.get(type);
        if (cached != null) {
            return cached;
        }
        boolean found = false;
        for (Field field : allFields(type)) {
            if (field.getAnnotation(RefText.class) != null) {
                found = true;
                break;
            }
            // 沿泛型类型深入：List<OrderItem> / Map<K,OrderItem> / OrderItem[] 等，
            // 需检查其元素类型而非 List/Map 本身，否则含嵌套集合的 DTO 会被误剪枝。
            if (genericContainsRefText(field.getGenericType(), seen, depth)) {
                found = true;
                break;
            }
        }
        CONTAINS_CACHE.put(type, found);
        return found;
    }

    /**
     * 沿泛型类型递归判断是否含 @RefText：拆解集合/Map/数组的元素类型（含通配符上界），
     * 对每个具体 Class 深入 {@link #containsRefText}。
     */
    private boolean genericContainsRefText(Type genericType, Set<Class<?>> seen, int depth) {
        if (genericType == null || depth > MAX_DEPTH) {
            return false;
        }
        if (genericType instanceof Class<?> clazz) {
            if (clazz.isArray()) {
                return genericContainsRefText(clazz.getComponentType(), seen, depth + 1);
            }
            return !isLeafType(clazz) && containsRefText(clazz, seen, depth + 1);
        }
        if (genericType instanceof ParameterizedType pt) {
            // 容器本身（List/Map 等）不深入，但其类型实参（元素类型）要逐个检查
            for (Type arg : pt.getActualTypeArguments()) {
                if (genericContainsRefText(arg, seen, depth + 1)) {
                    return true;
                }
            }
            return false;
        }
        if (genericType instanceof GenericArrayType gat) {
            return genericContainsRefText(gat.getGenericComponentType(), seen, depth + 1);
        }
        if (genericType instanceof WildcardType wt) {
            for (Type upper : wt.getUpperBounds()) {
                if (genericContainsRefText(upper, seen, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 扫描对象图并按类型批量预加载引用翻译。
     *
     * @param root 待序列化的对象（可为单对象、集合、分页结果等）
     */
    public void preload(Object root) {
        if (root == null || manager == null) {
            return;
        }
        // type -> ids 收集
        Map<String, Set<Object>> idsByType = new HashMap<>();
        collect(root, idsByType, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        // 每类型一次批量查询
        idsByType.forEach(manager::preload);
    }

    private void collect(Object obj, Map<String, Set<Object>> idsByType, Set<Object> visited, int depth) {
        if (obj == null || depth > MAX_DEPTH) {
            return;
        }
        if (obj instanceof Collection<?> collection) {
            for (Object item : collection) {
                collect(item, idsByType, visited, depth + 1);
            }
            return;
        }
        if (obj instanceof Map<?, ?> map) {
            for (Object v : map.values()) {
                collect(v, idsByType, visited, depth + 1);
            }
            return;
        }
        if (obj instanceof Object[] array) {
            for (Object item : array) {
                collect(item, idsByType, visited, depth + 1);
            }
            return;
        }
        Class<?> type = obj.getClass();
        // 跳过 JDK 内置类型（String/Number/时间/枚举等），它们不承载 @RefText
        if (isLeafType(type)) {
            return;
        }
        // 剪枝：整个类（含嵌套）都不含 @RefText 时，直接跳过其对象图，保证对无关响应零遍历成本
        if (!containsRefText(type)) {
            return;
        }
        if (!visited.add(obj)) {
            return;
        }
        // 收集本对象的 @RefText 字段值，并递归其嵌套 Bean 字段
        for (Field field : allFields(type)) {
            Object value = readField(field, obj);
            if (value == null) {
                continue;
            }
            RefText ref = field.getAnnotation(RefText.class);
            if (ref != null) {
                idsByType.computeIfAbsent(ref.value(), k -> new LinkedHashSet<>()).add(value);
            } else if (!isLeafType(field.getType()) || value instanceof Collection
                || value instanceof Map || value.getClass().isArray()) {
                collect(value, idsByType, visited, depth + 1);
            }
        }
    }

    /** 是否为无需深入的叶子类型 */
    private boolean isLeafType(Class<?> type) {
        return type.isPrimitive()
            || type.isEnum()
            || type.getName().startsWith("java.")
            || type.getName().startsWith("javax.")
            || type.getName().startsWith("jakarta.");
    }

    /** 该类全部字段（含父类），按类缓存 */
    private Field[] allFields(Class<?> type) {
        return REF_FIELD_CACHE.computeIfAbsent(type, clazz -> {
            java.util.List<Field> fields = new java.util.ArrayList<>();
            Class<?> current = clazz;
            while (current != null && current != Object.class) {
                for (Field f : current.getDeclaredFields()) {
                    int mod = f.getModifiers();
                    if (java.lang.reflect.Modifier.isStatic(mod) || java.lang.reflect.Modifier.isTransient(mod)) {
                        continue;
                    }
                    f.setAccessible(true);
                    fields.add(f);
                }
                current = current.getSuperclass();
            }
            return fields.isEmpty() ? EMPTY : fields.toArray(new Field[0]);
        });
    }

    private Object readField(Field field, Object obj) {
        try {
            return field.get(obj);
        } catch (Exception e) {
            return null;
        }
    }
}

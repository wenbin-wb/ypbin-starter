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
package cn.ypbin.starter.json.enums;

import cn.ypbin.starter.core.enums.BaseEnum;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * 枚举与 {@code value} 的双向映射索引。
 *
 * <p><b>为什么要按 {@code value} 而非 {@code name()} 传输</b>：{@link BaseEnum} 的契约就是
 * 「{@code value} 用于存储/传输」。若 JSON 走 {@code name()}，则前端拿到的是 {@code ENABLED}
 * 这类大写常量名——业务一改枚举常量名（重构、拼写修正）就会破坏对外契约；而 {@code value}
 * （{@code 1}/{@code 0} 或 {@code "enabled"}）是刻意设计的稳定标识。</p>
 *
 * <p><b>两处必须做对的细节</b>：</p>
 * <ul>
 *   <li><b>按 value 去重</b>：同一个 value 映射到两个常量时，反序列化结果取决于遍历顺序——
 *       这是静默的不确定性。构造索引时直接抛异常，让「枚举定义有误」在启动期暴露；</li>
 *   <li><b>值类型归一</b>：{@code getValue()} 可能返回 Integer / Long / String，而 JSON 里
 *       数字解析成 int/long 都可能，故索引键统一用 {@code String}（{@code String.valueOf}），
 *       比较时先把输入转成规范化字符串再查——避免「{@code 1} 与 {@code "1"} 不相等」这类
 *       看似能过、换个客户端就炸的差异。</li>
 * </ul>
 *
 * @param <E> 枚举类型
 * @author wenbin
 * @since 2026-10-18
 */
final class BaseEnumIndex<E extends Enum<E> & BaseEnum<?>> {

    private final Class<E> enumType;

    private final Map<String, E> byValue;

    private BaseEnumIndex(Class<E> enumType, Map<String, E> byValue) {
        this.enumType = enumType;
        this.byValue = byValue;
    }

    /**
     * 为指定枚举建立索引。
     *
     * @param enumType 枚举类型（必须实现 {@link BaseEnum}）
     * @param <E>      枚举类型
     * @return 索引
     * @throws IllegalStateException 存在重复 {@code value}
     */
    static <E extends Enum<E> & BaseEnum<?>> BaseEnumIndex<E> of(Class<E> enumType) {
        Objects.requireNonNull(enumType, "enumType");
        Map<String, E> byValue = new LinkedHashMap<>();
        for (E constant : enumType.getEnumConstants()) {
            Object value = constant.getValue();
            String key = canonical(value);
            E existing = byValue.putIfAbsent(key, constant);
            if (existing != null) {
                throw new IllegalStateException("枚举 " + enumType.getName() + " 存在重复的 value=" + key
                    + "（" + existing.name() + " 与 " + constant.name()
                    + "）；value 必须唯一，否则序列化/反序列化结果不确定");
            }
        }
        return new BaseEnumIndex<>(enumType, byValue);
    }

    /**
     * 枚举类型。
     *
     * @return 枚举类型
     */
    Class<E> enumType() {
        return enumType;
    }

    /**
     * 全部常量（顺序为声明顺序）。
     *
     * @return 常量列表（非 null）
     */
    List<E> constants() {
        return List.copyOf(enumType.getEnumConstants().length == 0
            ? List.of() : List.of(enumType.getEnumConstants()));
    }

    /**
     * 按 value 查常量。
     *
     * @param value value 的文本形态（可为 null，null 直接返回 null）
     * @return 匹配的常量，未匹配返回 {@code null}
     */
    @Nullable
    E findByValueText(@Nullable String value) {
        return value == null ? null : byValue.get(value);
    }

    /**
     * value 规范化：统一成 {@code String} 以便与 JSON 标量的文本形态比较。
     *
     * @param value 原始 value（可空）
     * @return 规范化文本；{@code null} 返回 {@code null}
     */
    @Nullable
    static String canonical(@Nullable Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

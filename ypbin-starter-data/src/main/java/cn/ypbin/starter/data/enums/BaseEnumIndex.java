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
package cn.ypbin.starter.data.enums;

import cn.ypbin.starter.core.enums.BaseEnum;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * 枚举与 {@code value} 的双向映射索引（data 模块版本）。
 *
 * <p>与 {@code ypbin-starter-json} 的同名类<b>不是</b>重复实现：data 模块不依赖 json 模块
 * （持久化不应反向依赖序列化），两处各自持有一份极小的索引逻辑，成本远低于为复用而引入模块耦合。
 * 语义保持完全一致：按 {@code value} 去重、键统一为 {@code String}。</p>
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
     * @param enumType 枚举类型
     * @param <E>      枚举类型
     * @return 索引
     * @throws IllegalStateException 存在重复 {@code value}
     */
    static <E extends Enum<E> & BaseEnum<?>> BaseEnumIndex<E> of(Class<E> enumType) {
        Objects.requireNonNull(enumType, "enumType");
        Map<String, E> byValue = new LinkedHashMap<>();
        for (E constant : enumType.getEnumConstants()) {
            String key = String.valueOf(constant.getValue());
            E existing = byValue.putIfAbsent(key, constant);
            if (existing != null) {
                throw new IllegalStateException("枚举 " + enumType.getName() + " 存在重复的 value=" + key
                    + "（" + existing.name() + " 与 " + constant.name()
                    + "）；value 必须唯一，否则落库/回填结果不确定");
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
     * 按 value 文本查常量。
     *
     * @param valueText value 文本（可为 null）
     * @return 匹配常量；未匹配或入参为 null 返回 {@code null}
     */
    @Nullable
    E findByValueText(@Nullable String valueText) {
        return valueText == null ? null : byValue.get(valueText);
    }

    /**
     * 全部 value 文本（错误信息用）。
     *
     * @return 逗号分隔的 value 列表
     */
    String valueTexts() {
        return byValue.keySet().stream().collect(Collectors.joining(", "));
    }
}

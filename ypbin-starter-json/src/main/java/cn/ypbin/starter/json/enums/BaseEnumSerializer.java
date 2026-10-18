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
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * {@link BaseEnum} 的 JSON 序列化器：输出 {@code getValue()}，而不是常量名。
 *
 * <p><b>与 {@code @JsonValue} 的关系</b>：本序列化器只在枚举<b>未</b>用 {@code @JsonValue}
 * 标注自身取值方法时装配（判断逻辑在 {@link BaseEnumModule}）。原因：{@code @JsonValue} 是业务
 * 显式声明的更强意图（例如想把枚举序列化成对象或改过格式的字符串），不能被starter的默认约定覆盖。</p>
 *
 * <p><b>null value 的处理</b>：{@code getValue()} 契约上非空（{@code @NullMarked} 模块内未标注即非空），
 * 但实现方可能在异常路径返回 null。此时写 JSON {@code null} 而不是抛异常——序列化是「尽力输出」
 * 的场景，为单个字段的脏值让整个响应 500 不合理。</p>
 *
 * @param <E> 枚举类型
 * @author wenbin
 * @since 2026-10-18
 */
final class BaseEnumSerializer<E extends Enum<E> & BaseEnum<?>> extends ValueSerializer<E> {

    private final BaseEnumIndex<E> index;

    BaseEnumSerializer(BaseEnumIndex<E> index) {
        this.index = index;
    }

    @Override
    public void serialize(E value, JsonGenerator generator, SerializationContext context) {
        Object raw = value.getValue();
        if (raw == null) {
            generator.writeNull();
            return;
        }
        context.writeValue(generator, raw);
    }

    /**
     * 序列化声明类型（供 {@code Serializers} 匹配用）。
     *
     * @return 枚举类型
     */
    Class<E> handledEnumType() {
        return index.enumType();
    }
}

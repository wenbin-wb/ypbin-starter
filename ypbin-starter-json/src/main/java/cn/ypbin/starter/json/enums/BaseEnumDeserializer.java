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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * {@link BaseEnum} 的 JSON 反序列化器：按 {@code getValue()} 匹配常量。
 *
 * <p><b>接受三种标量形态</b>：数字（{@code 1}）、字符串（{@code "1"} / {@code "enabled"}）、
 * 布尔（{@code true}）。统一先取文本形态再查索引，因此 {@code {"status": 1}} 与
 * {@code {"status": "1"}} 得到同一结果——前端把枚举当数字或字符串传都常见，若只认一种，
 * 换一个客户端/换一次序列化库就会静默变成 400 或 null。</p>
 *
 * <p><b>未知 value 必须失败</b>：不返回 {@code null}、不降级为第一个常量。静默返回 null 会让
 * 「状态写错了」变成「库里存了 null」，问题从入参校验漂移到数据层，排查成本高得多。</p>
 *
 * @param <E> 枚举类型
 * @author wenbin
 * @since 2026-10-18
 */
final class BaseEnumDeserializer<E extends Enum<E> & BaseEnum<?>> extends ValueDeserializer<E> {

    private final BaseEnumIndex<E> index;

    BaseEnumDeserializer(BaseEnumIndex<E> index) {
        this.index = index;
    }

    @Override
    @Nullable
    public E deserialize(JsonParser parser, DeserializationContext context) {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            // 显式 null 交由 Jackson 的「可空性」规则处理（引用类型字段得到 null）
            return null;
        }
        String text = scalarText(parser, token);
        if (text == null) {
            return context.reportInputMismatch(index.enumType(),
                "枚举 %s 只接受数字/字符串/布尔值，实际收到 %s",
                index.enumType().getSimpleName(), token);
        }
        E matched = index.findByValueText(text);
        if (matched == null) {
            return context.reportInputMismatch(index.enumType(),
                "枚举 %s 不支持 value=%s，可选值：%s",
                index.enumType().getSimpleName(), text,
                index.constants().stream()
                    .map(constant -> BaseEnumIndex.canonical(constant.getValue()))
                    .collect(Collectors.joining(", ")));
        }
        return matched;
    }

    /**
     * 取标量的文本形态。
     *
     * <p>不用 {@code parser.getValueAsString()}：它对非字符串标量会走通用的 toString，
     * 浮点/科学计数法在不同 Jackson 版本下形态不完全一致；这里显式按 token 分类取值，
     * 让「{@code 1.0} 是否等于 value {@code 1}」这类边界行为由我们自己决定并写明。</p>
     *
     * @param parser 解析器（当前 token 已是标量）
     * @param token  当前 token
     * @return 文本形态；非标量返回 {@code null}
     */
    @Nullable
    private static String scalarText(JsonParser parser, JsonToken token) {
        if (token == JsonToken.VALUE_STRING) {
            return parser.getString();
        }
        if (token == JsonToken.VALUE_TRUE) {
            return Boolean.TRUE.toString();
        }
        if (token == JsonToken.VALUE_FALSE) {
            return Boolean.FALSE.toString();
        }
        if (token != null && token.isNumeric()) {
            return parser.getNumberValue().toString();
        }
        return null;
    }

    /**
     * 反序列化声明类型（供 {@code Deserializers} 匹配用）。
     *
     * @return 枚举类型
     */
    Class<E> handledEnumType() {
        return index.enumType();
    }

    /**
     * 枚举常量名列表（错误信息用；包内可见便于测试断言文案形态）。
     *
     * @return 常量名列表
     */
    List<String> constantNames() {
        return Arrays.stream(index.enumType().getEnumConstants()).map(Enum::name).toList();
    }
}

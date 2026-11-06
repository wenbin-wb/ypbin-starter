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
package cn.ypbin.starter.json.sensitive;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * 脱敏序列化器（Jackson 3）。
 *
 * <p>通过 {@link #createContextual} 读取字段上的 {@link Sensitive} 注解，按不同字段的脱敏类型
 * 分别处理；无注解时退化为**该字段原始类型**的默认序列化器。</p>
 *
 * <p><strong>为什么泛型是 {@code Object} 而不是 {@code String}</strong>：{@code @Sensitive} 常被用在
 * {@code Long phone} / {@code Integer idCard} 这类非字符串字段上。原实现把回退写死成
 * {@code findValueSerializer(String.class)}，非目标字段拿到 String 序列化器后序列化 Long 会直接抛
 * {@code DatabindException: Long cannot be cast to String}，**一条字段打挂整个响应**。
 * 现在按 {@code property.getType()} 回退；命中注解时按 {@code String.valueOf(value)} 掩码输出
 * （掩码结果本身必然是字符串，例如 {@code 138****8000}）。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
// 由 Jackson 通过反射实例化并直接赋值属性字段（createContextual 中 new + 赋值），
// 构造器结束时字段尚为空属框架装配语义，故按类抑制 NullAway.Init
@SuppressWarnings("NullAway.Init")
public class SensitiveSerializer extends ValueSerializer<Object> {

    private SensitiveType type;
    private int prefixKeep;
    private int suffixKeep;

    @Override
    public ValueSerializer<?> createContextual(SerializationContext ctxt, BeanProperty property) {
        if (property == null) {
            return ctxt.findNullValueSerializer(property);
        }
        Sensitive annotation = property.getAnnotation(Sensitive.class);
        if (annotation == null) {
            annotation = property.getContextAnnotation(Sensitive.class);
        }
        if (annotation != null) {
            SensitiveSerializer serializer = new SensitiveSerializer();
            serializer.type = annotation.value();
            serializer.prefixKeep = annotation.prefixKeep();
            serializer.suffixKeep = annotation.suffixKeep();
            return serializer;
        }
        // 非目标字段：按字段**原始类型**回退（切勿写死 String.class，否则非 String 字段强转崩溃）
        return ctxt.findValueSerializer(property.getType());
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) {
        if (value == null) {
            gen.writeNull();
            return;
        }
        String text = String.valueOf(value);
        gen.writeString(type == null ? text : type.apply(text, prefixKeep, suffixKeep));
    }

    /**
     * 保留旧版 {@code ValueSerializer<String>} 的公开方法签名以维持二进制兼容。
     *
     * @param value 字段值
     * @param gen   JSON 生成器
     * @param ctxt  序列化上下文
     * @deprecated 使用 {@link #serialize(Object, JsonGenerator, SerializationContext)}
     */
    @Deprecated(since = "4.4.0", forRemoval = false)
    public void serialize(String value, JsonGenerator gen, SerializationContext ctxt) {
        serialize((Object) value, gen, ctxt);
    }
}

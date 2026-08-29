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
 * 分别处理；无注解时退化为原样输出。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class SensitiveSerializer extends ValueSerializer<String> {

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
        if (annotation != null && String.class.equals(property.getType().getRawClass())) {
            SensitiveSerializer serializer = new SensitiveSerializer();
            serializer.type = annotation.value();
            serializer.prefixKeep = annotation.prefixKeep();
            serializer.suffixKeep = annotation.suffixKeep();
            return serializer;
        }
        // 非目标字段：回退到默认 String 序列化
        return ctxt.findValueSerializer(String.class);
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializationContext ctxt) {
        if (type == null) {
            gen.writeString(value);
            return;
        }
        gen.writeString(type.apply(value, prefixKeep, suffixKeep));
    }
}

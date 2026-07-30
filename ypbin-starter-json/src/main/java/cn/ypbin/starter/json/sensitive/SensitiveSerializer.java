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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import java.io.IOException;

/**
 * 脱敏序列化器。
 *
 * <p>{@link ContextualSerializer} 让序列化器能读取字段上的 {@link Sensitive} 注解，
 * 从而按不同字段的脱敏类型分别处理。无注解时退化为原样输出。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
public class SensitiveSerializer extends JsonSerializer<String> implements ContextualSerializer {

    private SensitiveType type;
    private int prefixKeep;
    private int suffixKeep;

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (type == null) {
            gen.writeString(value);
            return;
        }
        gen.writeString(type.apply(value, prefixKeep, suffixKeep));
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property)
        throws JsonMappingException {
        if (property == null) {
            return prov.findNullValueSerializer(null);
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
        return prov.findValueSerializer(String.class, property);
    }
}

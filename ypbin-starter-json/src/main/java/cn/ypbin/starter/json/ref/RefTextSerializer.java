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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import java.io.IOException;

/**
 * 引用翻译序列化器。
 *
 * <p>{@link ContextualSerializer} 读取字段上的 {@link RefText} 注解：原字段原值照常输出（字段名不变），
 * 并额外写出展示名称字段（{@code 原字段名 + suffix}）。名称经 {@link RefTextUtils} 走缓存获取——列表场景
 * 若已由 {@link RefTextResolver} 预加载，此处全部命中缓存，无逐行回源。未接入引用翻译时不输出名称字段。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class RefTextSerializer extends JsonSerializer<Object> implements ContextualSerializer {

    private String refType;
    private String nameFieldName;

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        // 原字段原值照常输出，字段名不变（Long 交由默认序列化，保持大数转字符串等既有行为）
        serializers.defaultSerializeValue(value, gen);
        // 额外输出派生展示字段
        if (nameFieldName != null && value != null) {
            String name = RefTextUtils.translate(refType, value);
            if (name != null) {
                gen.writeStringField(nameFieldName, name);
            }
        }
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property)
        throws JsonMappingException {
        if (property == null) {
            return prov.findNullValueSerializer(null);
        }
        RefText annotation = property.getAnnotation(RefText.class);
        if (annotation == null) {
            annotation = property.getContextAnnotation(RefText.class);
        }
        if (annotation != null) {
            RefTextSerializer serializer = new RefTextSerializer();
            serializer.refType = annotation.value();
            serializer.nameFieldName = property.getName() + annotation.suffix();
            return serializer;
        }
        return prov.findValueSerializer(property.getType(), property);
    }
}

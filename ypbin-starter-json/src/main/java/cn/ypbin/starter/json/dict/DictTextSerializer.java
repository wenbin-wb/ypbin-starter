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
package cn.ypbin.starter.json.dict;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import java.io.IOException;

/**
 * 字典文本序列化器。
 *
 * <p>{@link ContextualSerializer} 读取字段上的 {@link DictText} 注解：原字段原值照常输出，并额外写出一个
 * 展示文本字段（{@code 原字段名 + suffix}）。翻译经 {@link DictUtils} 走缓存；未接入字典时展示文本回退为原值。</p>
 *
 * @author wenbin
 * @since 2026-08-01
 */
public class DictTextSerializer extends JsonSerializer<Object> implements ContextualSerializer {

    private String dictType;
    private String textFieldName;

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        // 原字段原值照常输出，字段名与类型不变（Integer/Long/String 等交由默认序列化）
        serializers.defaultSerializeValue(value, gen);
        // 额外输出派生展示字段：字典值统一按字符串 code 翻译
        if (textFieldName != null && value != null) {
            gen.writeStringField(textFieldName, DictUtils.translate(dictType, String.valueOf(value)));
        }
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider prov, BeanProperty property)
        throws JsonMappingException {
        if (property == null) {
            return prov.findNullValueSerializer(null);
        }
        DictText annotation = property.getAnnotation(DictText.class);
        if (annotation == null) {
            annotation = property.getContextAnnotation(DictText.class);
        }
        if (annotation != null) {
            // 支持 String / Integer / Long 等承载字典值的字段类型
            DictTextSerializer serializer = new DictTextSerializer();
            serializer.dictType = annotation.value();
            serializer.textFieldName = property.getName() + annotation.suffix();
            return serializer;
        }
        // 非目标字段：回退到原始类型的默认序列化器，切勿写死 String.class（否则非 String 字段强转崩溃）
        return prov.findValueSerializer(property.getType(), property);
    }
}

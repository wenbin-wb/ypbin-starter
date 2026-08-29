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
package cn.ypbin.starter.log.support;

import cn.ypbin.starter.log.annotation.LogMask;
import java.util.List;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;

/**
 * 日志专用 Jackson 3 模块：将标注 {@link LogMask} 的字段固定序列化为 {@code ******}。
 *
 * <p>只注册到 {@code AccessLogAspect} 使用的日志专用 {@code ObjectMapper}，不影响业务
 * 接口响应的正常序列化——同一个 DTO 打给前端仍是明文，打到日志里才会被掩码。</p>
 *
 * @author wenbin
 * @since 2026-08-07
 */
public class LogMaskModule extends SimpleModule {

    public LogMaskModule() {
        super("LogMaskModule");
        setSerializerModifier(new ValueSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                BeanDescription.Supplier beanDesc, List<BeanPropertyWriter> beanProperties) {
                for (BeanPropertyWriter writer : beanProperties) {
                    if (writer.getAnnotation(LogMask.class) != null) {
                        writer.assignSerializer(new MaskSerializer());
                    }
                }
                return beanProperties;
            }
        });
    }

    /**
     * 将字段值替换为固定掩码字符串（{@code null} 值由 JSON 配置决定是否输出，不受影响）。
     */
    private static final class MaskSerializer extends ValueSerializer<Object> {

        private static final String MASK = "******";

        @Override
        public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeString(MASK);
        }
    }
}

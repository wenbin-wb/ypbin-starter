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
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import java.util.List;

/**
 * 日志专用 Jackson 模块：将标注 {@link LogMask} 的字段固定序列化为 {@code ******}。
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
        setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config,
                BeanDescription beanDesc, List<BeanPropertyWriter> beanProperties) {
                for (int i = 0; i < beanProperties.size(); i++) {
                    BeanPropertyWriter writer = beanProperties.get(i);
                    if (writer.getAnnotation(LogMask.class) != null) {
                        beanProperties.set(i, new MaskedPropertyWriter(writer));
                    }
                }
                return beanProperties;
            }
        });
    }

    /**
     * 将字段值替换为固定掩码字符串，{@code null} 值仍按原逻辑处理（避免破坏 NON_NULL 等配置）。
     */
    private static final class MaskedPropertyWriter extends BeanPropertyWriter {

        private static final String MASK = "******";

        MaskedPropertyWriter(BeanPropertyWriter base) {
            super(base);
        }

        @Override
        public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
            Object value = get(bean);
            if (value == null) {
                super.serializeAsField(bean, gen, prov);
                return;
            }
            gen.writeFieldName(getName());
            gen.writeString(MASK);
        }
    }
}

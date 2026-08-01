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
package cn.ypbin.starter.json.autoconfigure;

import cn.ypbin.starter.json.dict.DictCache;
import cn.ypbin.starter.json.dict.DictProvider;
import cn.ypbin.starter.json.dict.DictUtils;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Jackson 统一序列化自动配置。
 *
 * <p>通过 {@link Jackson2ObjectMapperBuilderCustomizer} 定制 Spring Boot 默认 {@link ObjectMapper}：
 * <ul>
 *     <li>注册 JavaTime 模块并统一 {@code LocalDateTime/LocalDate/LocalTime} 的读写格式；</li>
 *     <li>可选将大数字（Long/BigInteger/BigDecimal）序列化为字符串，规避前端精度丢失；</li>
 *     <li>反序列化忽略未知字段、允许非标准转义，提升前后端兼容性。</li>
 * </ul>
 * 采用 customizer 并通过 {@code serializerByType} 精准覆盖具体类型，不调用 {@code builder.modules()}，
 * 从而保留 Spring Boot 自动发现并注册的其它模块（Jdk8Module、ParameterNamesModule 等）。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
@AutoConfigureBefore(org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class)
@ConditionalOnProperty(prefix = "ypbin.json", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JacksonProperties.class)
public class JacksonAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JacksonAutoConfiguration.class);

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer ypbinJacksonCustomizer(JacksonProperties properties) {
        log.debug("[ypbin-starter] jackson customizer applied.");
        return builder -> {
            // 反序列化容错
            builder.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            builder.featuresToEnable(JsonWriteFeature.ESCAPE_NON_ASCII.mappedFeature());

            // JavaTime 格式统一：仅覆盖具体类型的序列化/反序列化器，
            // 不 new 整个 JavaTimeModule 覆盖模块体系，保留 Boot 自动注册的其它模块。
            DateTimeFormatter dateTime = DateTimeFormatter.ofPattern(properties.getDateTimeFormat());
            DateTimeFormatter date = DateTimeFormatter.ofPattern(properties.getDateFormat());
            DateTimeFormatter time = DateTimeFormatter.ofPattern(properties.getTimeFormat());

            builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(dateTime));
            builder.deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(dateTime));
            builder.serializerByType(LocalDate.class, new LocalDateSerializer(date));
            builder.deserializerByType(LocalDate.class, new LocalDateDeserializer(date));
            builder.serializerByType(LocalTime.class, new LocalTimeSerializer(time));
            builder.deserializerByType(LocalTime.class, new LocalTimeDeserializer(time));

            // 大数字转字符串，规避 JS Number 精度丢失
            if (properties.isWriteBigNumberAsString()) {
                builder.serializerByType(Long.class, ToStringSerializer.instance);
                builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
                builder.serializerByType(BigInteger.class, ToStringSerializer.instance);
                builder.serializerByType(BigDecimal.class, ToStringSerializer.instance);
            }
        };
    }

    /**
     * 字典缓存：仅当业务方提供 {@link DictProvider}（如从字典表读）时装配，并绑定到 {@link DictUtils}
     * 供 {@code @DictText} 序列化器与静态调用使用。未接入字典时不装配，翻译安全退化为原值。
     *
     * @param dictProvider 字典数据来源
     * @return 字典缓存
     */
    @Bean
    @ConditionalOnBean(DictProvider.class)
    @ConditionalOnMissingBean
    public DictCache dictCache(DictProvider dictProvider) {
        DictCache dictCache = new DictCache(dictProvider);
        DictUtils.bind(dictCache);
        log.debug("[ypbin-starter] dict cache initialized.");
        return dictCache;
    }
}

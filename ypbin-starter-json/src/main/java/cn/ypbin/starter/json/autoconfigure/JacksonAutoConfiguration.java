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

import static tools.jackson.databind.ser.std.ToStringSerializer.instance;

import cn.ypbin.starter.json.dict.DictCache;
import cn.ypbin.starter.json.dict.DictProvider;
import cn.ypbin.starter.json.dict.DictUtils;
import cn.ypbin.starter.json.ref.RefTextCache;
import cn.ypbin.starter.json.ref.RefTextManager;
import cn.ypbin.starter.json.ref.RefTextProvider;
import cn.ypbin.starter.json.ref.RefTextResolver;
import cn.ypbin.starter.json.ref.RefTextResponseAdvice;
import cn.ypbin.starter.json.ref.RefTextUtils;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration;
import org.springframework.boot.jackson2.autoconfigure.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;
import tools.jackson.databind.module.SimpleModule;

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
@AutoConfigureBefore(Jackson2AutoConfiguration.class)
@ConditionalOnProperty(prefix = "ypbin.json", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JacksonProperties.class)
public class JacksonAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JacksonAutoConfiguration.class);

    /**
     * Jackson 3（SB4 主序列化器）定制：与 Jackson 2 customizer 等效，
     * 确保大数字转字符串、日期格式在 {@code tools.jackson.*} ObjectMapper 上同样生效。
     */
    @Bean
    @ConditionalOnClass(JsonMapperBuilderCustomizer.class)
    public JsonMapperBuilderCustomizer ypbinJackson3Customizer(JacksonProperties properties) {
        return builder -> {
            if (properties.isWriteBigNumberAsString()) {
                SimpleModule module = new SimpleModule("ypbin-big-number-as-string");
                module.addSerializer(Long.class, instance);
                module.addSerializer(Long.TYPE, instance);
                module.addSerializer(BigInteger.class, instance);
                module.addSerializer(BigDecimal.class, instance);
                builder.addModule(module);
            }
            // Jackson 3 内置 JavaTime 序列化器默认输出 ISO 格式（带 T），此处统一为
            // yyyy-MM-dd HH:mm:ss / yyyy-MM-dd / HH:mm:ss，与 Jackson 2 customizer 行为一致。
            SimpleModule javaTimeModule = new SimpleModule("ypbin-java-time-format");
            DateTimeFormatter dateTime = DateTimeFormatter.ofPattern(properties.getDateTimeFormat());
            DateTimeFormatter date = DateTimeFormatter.ofPattern(properties.getDateFormat());
            DateTimeFormatter time = DateTimeFormatter.ofPattern(properties.getTimeFormat());
            javaTimeModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(dateTime));
            javaTimeModule.addDeserializer(LocalDateTime.class,
                new LocalDateTimeDeserializer(dateTime));
            javaTimeModule.addSerializer(LocalDate.class,
                new LocalDateSerializer(date));
            javaTimeModule.addDeserializer(LocalDate.class,
                new LocalDateDeserializer(date));
            javaTimeModule.addSerializer(LocalTime.class,
                new LocalTimeSerializer(time));
            javaTimeModule.addDeserializer(LocalTime.class,
                new LocalTimeDeserializer(time));
            builder.addModule(javaTimeModule);
        };
    }

    @Bean
    @ConditionalOnMissingBean
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

            // Jackson 2（spring-boot-jackson2 兼容层）JavaTime 格式统一；SB4 主序列化器走
            // {@link #ypbinJackson3Customizer}。两套类名同源不同包，此处按兼容层内联引用。
            builder.serializerByType(LocalDateTime.class,
                new com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer(dateTime));
            builder.deserializerByType(LocalDateTime.class,
                new com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer(dateTime));
            builder.serializerByType(LocalDate.class,
                new com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer(date));
            builder.deserializerByType(LocalDate.class,
                new com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer(date));
            builder.serializerByType(LocalTime.class,
                new com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer(time));
            builder.deserializerByType(LocalTime.class,
                new com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer(time));

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

    /**
     * 引用翻译管理器：仅当业务方提供 {@link RefTextProvider}（如用户表、部门表数据源）时装配，
     * 内置带 TTL 与容量上限的缓存并绑定到 {@link RefTextUtils} 供 {@code @RefText} 序列化器使用。
     * 未接入时不装配，翻译安全退化。
     *
     * @param providers 引用数据来源列表
     * @param properties JSON 配置
     * @return 引用翻译管理器
     */
    @Bean
    @ConditionalOnBean(RefTextProvider.class)
    @ConditionalOnMissingBean
    public RefTextManager refTextManager(List<RefTextProvider> providers, JacksonProperties properties) {
        JacksonProperties.RefText config = properties.getRefText();
        RefTextCache cache = new RefTextCache(Duration.ofSeconds(config.getTtlSeconds()).toMillis(),
            config.getMaxSize());
        RefTextManager manager = new RefTextManager(providers, cache);
        RefTextUtils.bind(manager);
        log.debug("[ypbin-starter] ref-text manager initialized, providers={}.", providers.size());
        return manager;
    }

    /**
     * 引用翻译预加载解析器：列表/分页序列化前批量预热，消除逐行 N+1 回源。
     *
     * @param manager 引用翻译管理器
     * @return 预加载解析器
     */
    @Bean
    @ConditionalOnBean(RefTextManager.class)
    @ConditionalOnMissingBean
    public RefTextResolver refTextResolver(RefTextManager manager) {
        return new RefTextResolver(manager);
    }

    /**
     * 引用翻译自动预加载装配：仅在 Servlet Web 环境、类路径存在 ResponseBodyAdvice、存在
     * {@link RefTextResolver}、且 {@code ypbin.json.ref-text.auto-resolve=true}（默认）时生效。
     * 业务无需手动 preload 即享列表零 N+1 翻译。
     */
    @AutoConfiguration
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(ResponseBodyAdvice.class)
    @ConditionalOnProperty(prefix = "ypbin.json.ref-text", name = "auto-resolve", havingValue = "true",
        matchIfMissing = true)
    static class RefTextWebConfiguration {

        @Bean
        @ConditionalOnBean(RefTextResolver.class)
        @ConditionalOnMissingBean
        public RefTextResponseAdvice refTextResponseAdvice(RefTextResolver resolver) {
            return new RefTextResponseAdvice(resolver);
        }
    }
}

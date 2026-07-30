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
package cn.ypbin.starter.cache.autoconfigure;

import cn.ypbin.starter.cache.core.CacheService;
import cn.ypbin.starter.cache.redis.RedisCacheService;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 缓存自动配置。
 *
 * <p>提供 String 键 + JSON 值序列化的 {@link RedisTemplate}，并在其上封装
 * {@link CacheService}。声明在 Spring Boot {@link RedisAutoConfiguration} 之前，
 * 以便自定义的 RedisTemplate 优先生效；所有 Bean 均可被业务方覆盖。</p>
 *
 * <p>Redis 值序列化器复用容器中的 {@link ObjectMapper}（继承 json 模块的时间/大数字规则），
 * 但使用其 {@link ObjectMapper#copy() 副本} 并在副本上开启多态类型信息（default typing），
 * 从而保证反序列化时能还原具体类型，且不污染 Spring MVC 使用的共享 ObjectMapper。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration(before = RedisAutoConfiguration.class)
@ConditionalOnClass(RedisTemplate.class)
@ConditionalOnProperty(prefix = "ypbin.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CacheAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CacheAutoConfiguration.class);

    /**
     * 定制化 RedisTemplate：键用 String，值用 JSON，便于跨语言可读与调试。
     *
     * @param connectionFactory Redis 连接工厂
     * @param objectMapperProvider 容器中的 ObjectMapper（可能不存在，做兜底）
     */
    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                       ObjectProvider<ObjectMapper> objectMapperProvider) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        RedisSerializer<String> keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer =
            new GenericJackson2JsonRedisSerializer(buildRedisObjectMapper(objectMapperProvider));

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        log.debug("[ypbin-starter] redisTemplate (string key / json value) configured.");
        return template;
    }

    /**
     * 构建 Redis 专用 ObjectMapper：优先复用容器共享实例的副本，开启多态类型信息。
     *
     * <p>使用副本 + 独立开启 default typing，避免影响 MVC 的 JSON 输出（否则 HTTP
     * 响应会混入 {@code @class} 类型字段）。多态校验器仅信任所有类型以支持任意缓存对象，
     * 由于缓存内容由服务端自身写入，不存在反序列化外部不可信数据的风险。</p>
     */
    private ObjectMapper buildRedisObjectMapper(ObjectProvider<ObjectMapper> objectMapperProvider) {
        ObjectMapper shared = objectMapperProvider.getIfAvailable();
        ObjectMapper redisMapper = (shared != null) ? shared.copy() : new ObjectMapper();
        redisMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        redisMapper.activateDefaultTyping(
            BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(),
            ObjectMapper.DefaultTyping.NON_FINAL,
            JsonTypeInfo.As.PROPERTY);
        return redisMapper;
    }

    @Bean
    @ConditionalOnMissingBean
    public CacheService cacheService(RedisTemplate<String, Object> redisTemplate) {
        return new RedisCacheService(redisTemplate);
    }
}

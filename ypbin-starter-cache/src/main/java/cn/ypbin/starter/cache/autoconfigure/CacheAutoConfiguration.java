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

import cn.ypbin.starter.cache.annotation.CacheEvictAspect;
import cn.ypbin.starter.cache.core.CacheService;
import cn.ypbin.starter.cache.multilevel.MultiLevelCacheProperties;
import cn.ypbin.starter.cache.redis.RedisCacheService;
import cn.ypbin.starter.cache.redis.RedisJsonSerializerFactory;
import org.aspectj.lang.JoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 缓存自动配置。
 *
 * <p>提供 String 键 + JSON 值序列化的 {@link RedisTemplate}，并在其上封装
 * {@link CacheService}。声明在 Spring Boot {@link RedisAutoConfiguration} 之前，
 * 以便自定义的 RedisTemplate 优先生效；所有 Bean 均可被业务方覆盖。</p>
 *
 * <p>Redis 值序列化器以容器 {@link JsonMapper} 的构建器副本为基底（继承 json 模块的时间/大数字规则），
 * 多态类型信息由 {@link RedisJsonSerializerFactory} 统一交给序列化器自身管理——不可在传入的 mapper 上
 * 再次 {@code activateDefaultTyping}，否则读写两侧类型标识形态不一致会导致集合类缓存值反序列化失败。
 * 配置落在独立 mapper 上，不会污染 Spring MVC 使用的共享 JsonMapper。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration(before = DataRedisAutoConfiguration.class)
@ConditionalOnClass(RedisTemplate.class)
@ConditionalOnProperty(prefix = CacheAutoConfiguration.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class CacheAutoConfiguration {

    /**
     * 配置前缀。
     *
     * <p>本模块没有绑定配置属性的 {@code @ConfigurationProperties} 类（{@code ypbin.cache.multi-level.*}
     * 属多级缓存，见 {@code MultiLevelCacheProperties}），故前缀常量就近定义在本自动配置类内，
     * 供条件注解引用，避免字面量散落。</p>
     */
    public static final String PREFIX = "ypbin.cache";

    private static final Logger log = LoggerFactory.getLogger(CacheAutoConfiguration.class);

    /**
     * 定制化 RedisTemplate：键用 String，值用 JSON，便于跨语言可读与调试。
     *
     * @param connectionFactory Redis 连接工厂
     * @param jsonMapperProvider 容器中的 Jackson 3 JsonMapper（可能不存在，做兜底）
     */
    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                       ObjectProvider<JsonMapper> jsonMapperProvider) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        RedisSerializer<String> keySerializer = new StringRedisSerializer();
        RedisSerializer<Object> valueSerializer =
            RedisJsonSerializerFactory.create(jsonMapperProvider.getIfAvailable());

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        log.debug("[ypbin-starter] redisTemplate (string key / json value) configured.");
        return template;
    }

    /**
     * 默认（单级）缓存服务：String 键 + JSON 值的 Redis 实现。
     *
     * <p><strong>为什么多级缓存启用时必须让位</strong>：{@code MultiLevelCacheAutoConfiguration#cacheService}
     * 的 {@code @Bean} 方法名与本方法同名（都是 {@code cacheService}），而多级配置声明为
     * {@code @AutoConfiguration(after = CacheAutoConfiguration.class)}，本方法必然先注册；Spring Boot 默认
     * 禁止 Bean 定义覆盖（{@code allow-bean-definition-overriding=false}），第二个同名定义会抛
     * {@code BeanDefinitionOverrideException}，宿主一开 {@code ypbin.cache.multi-level.enabled=true} 就启动失败。
     * 类型级 {@code @ConditionalOnMissingBean} 在多级配置求值时还看不到 {@code MultiLevelCacheService}，
     * 挡不住这条同名冲突。故这里显式按「多级启用则让位」排除：容器中始终只有一个 {@link CacheService}，
     * Bean 名仍是 {@code cacheService}（宿主按名注入不破），实际实现由多级配置提供。</p>
     *
     * @param redisTemplate String 键 + JSON 值的 Redis 模板
     * @return 单级缓存服务
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = MultiLevelCacheProperties.PREFIX, name = "enabled",
        havingValue = "false", matchIfMissing = true)
    public CacheService cacheService(RedisTemplate<String, Object> redisTemplate) {
        return new RedisCacheService(redisTemplate);
    }

    /**
     * {@link CacheEvict} 声明式缓存失效切面（有 aspectj 时生效，可被宿主覆盖）。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(JoinPoint.class)
    public CacheEvictAspect cacheEvictAspect() {
        return new CacheEvictAspect();
    }
}

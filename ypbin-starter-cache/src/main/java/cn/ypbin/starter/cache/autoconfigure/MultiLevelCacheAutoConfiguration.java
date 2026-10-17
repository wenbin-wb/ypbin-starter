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
import cn.ypbin.starter.cache.multilevel.CacheInvalidationListener;
import cn.ypbin.starter.cache.multilevel.CacheInvalidationPublisher;
import cn.ypbin.starter.cache.multilevel.CacheMetricsRecorder;
import cn.ypbin.starter.cache.multilevel.MicrometerCacheMetrics;
import cn.ypbin.starter.cache.multilevel.MultiLevelCacheProperties;
import cn.ypbin.starter.cache.multilevel.MultiLevelCacheService;
import cn.ypbin.starter.cache.multilevel.RedisCacheInvalidationPublisher;
import cn.ypbin.starter.cache.redis.RedisCacheService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 多级缓存自动配置。
 *
 * <p>仅在类路径同时存在 Caffeine 与 spring-data-redis 且 {@code ypbin.cache.multi-level.enabled=true} 时生效：
 * 以 L1（Caffeine）包裹 L2（Redis {@link RedisCacheService}）构成多级缓存，并覆盖默认 {@link CacheService} Bean。
 * 开启失效广播时，基于 Redis Pub/Sub 在多实例间同步 L1 失效。</p>
 *
 * <p><strong>类级 {@code @ConditionalOnClass} 必须同时纳入 {@link StringRedisTemplate}：</strong>本类多个
 * {@code @Bean} 方法签名直接引用 Redis 类型（{@code StringRedisTemplate}/{@code RedisTemplate}/
 * {@code RedisConnectionFactory} 等，同属 spring-data-redis）。方法级条件拦不住 Spring 对配置类的方法内省，
 * 若只守 Caffeine、在「有 Caffeine 无 Redis」时整类会被内省，加载 Redis 类型即抛 {@code NoClassDefFoundError}。
 * 把 Redis 纳入类级条件后，缺 Redis 时整类在内省前跳过。多级缓存 = L1 Caffeine + L2 Redis，缺任一不装配也符合语义。</p>
 *
 * @author wenbin
 * @since 2026-07-31
 */
@AutoConfiguration(after = CacheAutoConfiguration.class)
@ConditionalOnClass({Caffeine.class, StringRedisTemplate.class})
@ConditionalOnProperty(prefix = MultiLevelCacheProperties.PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MultiLevelCacheProperties.class)
public class MultiLevelCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "l1Cache")
    public Cache<String, Object> l1Cache(MultiLevelCacheProperties properties) {
        return Caffeine.newBuilder()
            .maximumSize(properties.getLocalMaxSize())
            .expireAfterWrite(Duration.ofSeconds(properties.getLocalExpireSeconds()))
            .build();
    }

    /** 实例唯一标识，用于失效广播忽略自身消息 */
    @Bean
    @ConditionalOnMissingBean(name = "cacheInstanceId")
    public String cacheInstanceId() {
        return UUID.randomUUID().toString();
    }

    @Bean
    @ConditionalOnMissingBean(CacheInvalidationPublisher.class)
    @ConditionalOnProperty(prefix = MultiLevelCacheProperties.PREFIX, name = "invalidation-broadcast",
        havingValue = "true", matchIfMissing = true)
    public CacheInvalidationPublisher cacheInvalidationPublisher(StringRedisTemplate stringRedisTemplate,
            MultiLevelCacheProperties properties, String cacheInstanceId) {
        return new RedisCacheInvalidationPublisher(stringRedisTemplate, properties.getInvalidationChannel(),
            cacheInstanceId);
    }

    /**
     * 多级缓存服务，覆盖默认 CacheService。L2 直接 new RedisCacheService，避免依赖被本 Bean 覆盖前的 CacheService。
     */
    @Bean
    @ConditionalOnMissingBean(MultiLevelCacheService.class)
    public MultiLevelCacheService cacheService(RedisTemplate<String, Object> redisTemplate,
            Cache<String, Object> l1Cache,
            ObjectProvider<CacheInvalidationPublisher> publisher,
            ObjectProvider<CacheMetricsRecorder> metricsRecorder) {
        CacheService l2 = new RedisCacheService(redisTemplate);
        return new MultiLevelCacheService(l2, l1Cache, publisher.getIfAvailable(),
            metricsRecorder.getIfAvailable(() -> CacheMetricsRecorder.NOOP));
    }

    /**
     * 失效广播订阅容器：收到其它实例的失效消息后摘除本地 L1。
     */
    @Bean
    @ConditionalOnBean(CacheInvalidationPublisher.class)
    @ConditionalOnMissingBean(RedisMessageListenerContainer.class)
    public RedisMessageListenerContainer cacheInvalidationListenerContainer(RedisConnectionFactory connectionFactory,
            MultiLevelCacheService cacheService, MultiLevelCacheProperties properties, String cacheInstanceId) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(new CacheInvalidationListener(cacheService, cacheInstanceId),
            new ChannelTopic(properties.getInvalidationChannel()));
        return container;
    }

    /**
     * Micrometer 运行时指标装配（可选依赖）。
     *
     * <p><strong>为什么必须放在类级 {@code @ConditionalOnClass} 的嵌套配置里</strong>：
     * {@code micrometer-core} 是 optional 依赖，方法级条件拦不住 Spring 对配置类的方法内省——本嵌套类的
     * {@code @Bean} 方法体引用 {@link MicrometerCacheMetrics}（其字节码引用 Micrometer 类型），若在未引入
     * micrometer 的宿主上被内省，可能在类校验阶段抛 {@code NoClassDefFoundError}。类级条件在内省方法之前
     * 判定，缺依赖时整类跳过（与 {@code RedisLeaseLockStoreConfiguration} 同一处理）。</p>
     *
     * <p><strong>为什么用 {@link ObjectProvider} 而不是 {@code @ConditionalOnBean(MeterRegistry.class)}</strong>：
     * 后者会引入本仓反复出现的「顺序陷阱」——{@code MeterRegistry} 由 Boot 的 metrics 自动配置提供，
     * 若不声明 {@code @AutoConfigureAfter} 指向它，条件求值时机早于提供者 ⇒ 守卫恒假、指标静默不装配。
     * 用 {@link ObjectProvider} 弱依赖在 Bean 创建时取值，既不依赖条件求值顺序，也能在「引了 micrometer
     * 但宿主没注册 MeterRegistry」时干净退化为空实现。</p>
     *
     * <p><strong>为什么 {@code cache} 标签值取自配置而非写死常量</strong>：写死 {@code multi-level} 会让多副本
     * 的指标聚合到同一序列，排障时看不出是哪台机器的缓存异常；标签值改由
     * {@link MultiLevelCacheProperties#getMetricsCacheName()} 提供（默认值
     * {@link MultiLevelCacheProperties#DEFAULT_METRICS_CACHE_NAME} 与旧常量一致，未配置时行为不变），
     * 实例身份的注入交给宿主的占位符（如 {@code ${HOSTNAME}}），本模块不猜实例名——猜错会把同一逻辑缓存
     * 的指标打散成无数序列。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    static class CacheMetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public CacheMetricsRecorder cacheMetricsRecorder(ObjectProvider<MeterRegistry> registryProvider,
                MultiLevelCacheProperties properties) {
            MeterRegistry registry = registryProvider.getIfAvailable();
            return registry == null
                ? CacheMetricsRecorder.NOOP
                : MicrometerCacheMetrics.of(registry, properties.getMetricsCacheName());
        }
    }
}

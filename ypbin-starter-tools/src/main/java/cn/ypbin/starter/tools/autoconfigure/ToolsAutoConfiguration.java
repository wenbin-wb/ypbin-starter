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
package cn.ypbin.starter.tools.autoconfigure;

import cn.ypbin.starter.tools.idempotent.IdempotentAspect;
import cn.ypbin.starter.tools.idempotent.IdempotentStore;
import cn.ypbin.starter.tools.idempotent.InMemoryIdempotentStore;
import cn.ypbin.starter.tools.idempotent.RedisIdempotentStore;
import cn.ypbin.starter.tools.limiter.InMemoryRateLimiterStore;
import cn.ypbin.starter.tools.limiter.RateLimitAspect;
import cn.ypbin.starter.tools.limiter.RateLimiterStore;
import cn.ypbin.starter.tools.limiter.RedisRateLimiterStore;
import cn.ypbin.starter.tools.lock.DistributedLockAspect;
import cn.ypbin.starter.tools.lock.InMemoryLockService;
import cn.ypbin.starter.tools.lock.LockService;
import cn.ypbin.starter.tools.lock.RedisLockService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 工具模块自动配置。
 *
 * <p>装配限流能力与限流切面。限流存储优先使用 Redis + Lua 分布式实现（存在 Redis 时），
 * 否则退化为本地内存实现；两者均可被业务方自定义 Bean 覆盖。仅在 AOP 存在且
 * {@code ypbin.tools.rate-limit.enabled=true}（默认开启）时生效。</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnClass(org.aspectj.lang.ProceedingJoinPoint.class)
public class ToolsAutoConfiguration {

    /**
     * Redis 分布式限流存储：存在 Redis 且开启分布式（默认）时优先装配。
     *
     * <p>方法声明在内存兜底之前：同一配置类内 {@code @Bean} 方法按声明顺序处理，
     * 该方法先注册 {@link RateLimiterStore} 后，内存兜底方法的 {@code @ConditionalOnMissingBean}
     * 便不再生效。此顺序是 Spring 文档保证可靠的用法。</p>
     */
    @Bean
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnMissingBean(RateLimiterStore.class)
    @ConditionalOnProperty(prefix = "ypbin.tools.rate-limit", name = "distributed", havingValue = "true", matchIfMissing = true)
    public RateLimiterStore redisRateLimiterStore(StringRedisTemplate redisTemplate) {
        return new RedisRateLimiterStore(redisTemplate);
    }

    /**
     * 内存限流存储：兜底，仅当容器中不存在任何 {@link RateLimiterStore} 时装配。
     */
    @Bean
    @ConditionalOnMissingBean(RateLimiterStore.class)
    public RateLimiterStore inMemoryRateLimiterStore() {
        return new InMemoryRateLimiterStore();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ypbin.tools.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RateLimitAspect rateLimitAspect(RateLimiterStore store) {
        return new RateLimitAspect(store);
    }

    /**
     * Redis 分布式幂等存储：存在 Redis 时优先装配（声明在内存兜底之前，顺序保证可靠）。
     */
    @Bean
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnMissingBean(IdempotentStore.class)
    @ConditionalOnProperty(prefix = "ypbin.tools.idempotent", name = "distributed", havingValue = "true", matchIfMissing = true)
    public IdempotentStore redisIdempotentStore(StringRedisTemplate redisTemplate) {
        return new RedisIdempotentStore(redisTemplate);
    }

    /**
     * 内存幂等存储：兜底，仅当容器中不存在任何 {@link IdempotentStore} 时装配。
     */
    @Bean
    @ConditionalOnMissingBean(IdempotentStore.class)
    public IdempotentStore inMemoryIdempotentStore() {
        return new InMemoryIdempotentStore();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ypbin.tools.idempotent", name = "enabled", havingValue = "true", matchIfMissing = true)
    public IdempotentAspect idempotentAspect(IdempotentStore store) {
        return new IdempotentAspect(store);
    }

    /**
     * Redis 分布式锁：存在 Redis 时优先装配（声明在内存兜底之前，顺序保证可靠）。
     */
    @Bean
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnMissingBean(LockService.class)
    public LockService redisLockService(StringRedisTemplate redisTemplate) {
        return new RedisLockService(redisTemplate);
    }

    /**
     * 内存单机锁：兜底，仅当容器中不存在任何 {@link LockService} 时装配。
     */
    @Bean
    @ConditionalOnMissingBean(LockService.class)
    public LockService inMemoryLockService() {
        return new InMemoryLockService();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ypbin.tools.lock", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DistributedLockAspect distributedLockAspect(LockService lockService) {
        return new DistributedLockAspect(lockService);
    }
}

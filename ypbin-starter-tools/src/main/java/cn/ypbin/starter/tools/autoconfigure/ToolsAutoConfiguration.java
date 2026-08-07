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
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 工具模块自动配置。
 *
 * <p>装配限流 / 幂等 / 分布式锁能力及其切面。三类存储均优先用 Redis 分布式实现（存在 Redis 时），
 * 否则退化为本地内存实现；两者均可被业务方自定义 Bean 覆盖。仅在 AOP 存在时生效。</p>
 *
 * <p><strong>为何把 Redis 实现放进嵌套的 {@link RedisStoreConfiguration}（而非本类的 {@code @Bean} 方法）：</strong>
 * 方法级 {@code @ConditionalOnClass} 只能阻止 Bean 注册，阻止不了 Spring 对配置类做方法内省——处理配置类时
 * 会加载所有 {@code @Bean} 方法的参/返回类型，若签名里含 {@code StringRedisTemplate} 而 classpath 无
 * spring-data-redis，内省阶段即抛 {@code NoClassDefFoundError}，条件根本来不及生效。故 Redis 相关 Bean 收拢到
 * <strong>类级</strong> {@code @ConditionalOnClass(StringRedisTemplate.class)} 的嵌套配置：类级条件在内省该类方法
 * <em>之前</em>评估，无 Redis 时整个类被跳过，本类内省时不碰任何 Redis 类型。这是隔离可选依赖的标准做法。</p>
 *
 * <p><strong>装配顺序：</strong>用 {@code @Import} 引入 Redis 配置——被 import 的配置类先于本类自身的
 * {@code @Bean} 方法注册，故 Redis 存储先注册，内存兜底的 {@code @ConditionalOnMissingBean} 便正确退让。
 * （不依赖嵌套类声明顺序，那不可靠。）</p>
 *
 * @author wenbin
 * @since 2026-07-30
 */
@AutoConfiguration
@ConditionalOnClass(ProceedingJoinPoint.class)
@Import(ToolsAutoConfiguration.RedisStoreConfiguration.class)
public class ToolsAutoConfiguration {

    /**
     * 内存限流存储：兜底，仅当容器中不存在任何 {@link RateLimiterStore} 时装配
     * （Redis 实现存在时即退让）。
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

    /**
     * Redis 分布式存储配置。类级 {@code @ConditionalOnClass(StringRedisTemplate.class)}：无 Redis 时整个类被
     * 跳过、其 {@code @Bean} 方法不被内省，从而外层配置在无 spring-data-redis 环境下也能正常启动。
     *
     * <p>各 Redis Bean 带 {@code @ConditionalOnMissingBean}，且经 {@code @Import} 先于外层内存兜底注册，
     * 故存在 Redis 时优先生效、内存兜底退让；{@code distributed=false} 可强制走内存实现。</p>
     *
     * <p>类级 {@code @ConditionalOnClass} 只判断 classpath 是否有该类，不代表容器里真有可用的
     * {@link StringRedisTemplate} Bean（如未配置 Redis 连接、或测试环境只引入了依赖未装配自动配置）。
     * 叠加 {@code @ConditionalOnBean(StringRedisTemplate.class)}，两者都满足才展开本配置，避免 Redis Bean
     * 因缺少注入源在启动期抛 {@code UnsatisfiedDependencyException}。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    static class RedisStoreConfiguration {

        /**
         * Redis 分布式限流存储：存在 Redis 且开启分布式（默认）时优先装配。
         */
        @Bean
        @ConditionalOnMissingBean(RateLimiterStore.class)
        @ConditionalOnProperty(prefix = "ypbin.tools.rate-limit", name = "distributed", havingValue = "true", matchIfMissing = true)
        public RateLimiterStore redisRateLimiterStore(StringRedisTemplate redisTemplate) {
            return new RedisRateLimiterStore(redisTemplate);
        }

        /**
         * Redis 分布式幂等存储：存在 Redis 且开启分布式（默认）时优先装配。
         */
        @Bean
        @ConditionalOnMissingBean(IdempotentStore.class)
        @ConditionalOnProperty(prefix = "ypbin.tools.idempotent", name = "distributed", havingValue = "true", matchIfMissing = true)
        public IdempotentStore redisIdempotentStore(StringRedisTemplate redisTemplate) {
            return new RedisIdempotentStore(redisTemplate);
        }

        /**
         * Redis 分布式锁：存在 Redis 时优先装配。
         */
        @Bean
        @ConditionalOnMissingBean(LockService.class)
        public LockService redisLockService(StringRedisTemplate redisTemplate) {
            return new RedisLockService(redisTemplate);
        }
    }
}

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

import cn.ypbin.starter.tools.lock.LeaseLockGuard;
import cn.ypbin.starter.tools.lock.LeaseLockProperties;
import cn.ypbin.starter.tools.lock.LeaseLockService;
import cn.ypbin.starter.tools.lock.LeaseLockServiceImpl;
import cn.ypbin.starter.tools.lock.LeaseRenewalScheduler;
import cn.ypbin.starter.tools.lock.NoopLeaseLockService;
import cn.ypbin.starter.tools.lock.ScheduledLeaseRenewalScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 租约锁（长任务分布式互斥）自动配置。
 *
 * <p>装配 {@link LeaseLockService}（有 Redis ⇒ 多实例正确；无 Redis ⇒ 进程内兜底并打 WARN）、
 * 续期看门狗调度器 {@link LeaseRenewalScheduler}，以及任务守卫 {@link LeaseLockGuard}
 * （仅当容器里存在 {@link MeterRegistry} 时装配——指标是可选的观测能力，不该成为使用锁的前置条件）。
 * 所有 Bean 均可被业务方自定义覆盖。</p>
 *
 * <p><strong>为何把 Redis 实现放进嵌套的 {@link RedisLeaseLockStoreConfiguration}（而非本类的 {@code @Bean} 方法）：</strong>
 * 方法级 {@code @ConditionalOnClass} 只能阻止 Bean 注册，阻止不了 Spring 对配置类做方法内省——处理配置类时
 * 会加载所有 {@code @Bean} 方法的参/返回类型，若签名里含 {@code StringRedisTemplate} 而 classpath 无
 * spring-data-redis，内省阶段即抛 {@code NoClassDefFoundError}，条件根本来不及生效。故 Redis 相关 Bean 收拢到
 * <strong>类级</strong> {@code @ConditionalOnClass(StringRedisTemplate.class)} 的嵌套配置：类级条件在内省该类方法
 * <em>之前</em>评估，无 Redis 时整个类被跳过。这是隔离可选依赖的标准做法（与
 * {@code ToolsAutoConfiguration.RedisStoreConfiguration} 同款）。</p>
 *
 * <p><strong>装配顺序：</strong>用 {@code @Import} 引入 Redis 配置——被 import 的配置类先于本类自身的
 * {@code @Bean} 方法注册，故 Redis 实现先注册，内存兜底的 {@code @ConditionalOnMissingBean} 便正确退让。</p>
 *
 * <p><strong>启动自检：</strong>{@code renew-interval} 必须小于 {@code ttl}，否则锁会在第一次续期发生之前
 * 过期，表现为「偶尔两个副本同时执行」——这种配置错误只能在启动时判，绝不能留到生产上随机复现。
 * 另外校验 {@code ttl} 与 {@code renew-interval} 为正、{@code key-prefix} 非空。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
@AutoConfiguration
@EnableConfigurationProperties(LeaseLockProperties.class)
@Import(LeaseLockAutoConfiguration.RedisLeaseLockStoreConfiguration.class)
public class LeaseLockAutoConfiguration implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(LeaseLockAutoConfiguration.class);

    private final LeaseLockProperties properties;

    public LeaseLockAutoConfiguration(LeaseLockProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        if (!properties.isEnabled()) {
            log.warn("[ypbin] 租约锁已关闭（{}.enabled=false）：每个副本都会执行这些任务，"
                + "**仅单实例部署**可这么配", LeaseLockProperties.PREFIX);
            return;
        }
        if (properties.getTtl() == null || properties.getTtl().isZero() || properties.getTtl().isNegative()) {
            throw new IllegalStateException(LeaseLockProperties.PREFIX + ".ttl 必须为正数");
        }
        if (properties.getRenewInterval() == null || properties.getRenewInterval().isZero()
            || properties.getRenewInterval().isNegative()) {
            throw new IllegalStateException(LeaseLockProperties.PREFIX + ".renew-interval 必须为正数");
        }
        if (properties.getRenewInterval().compareTo(properties.getTtl()) >= 0) {
            throw new IllegalStateException(LeaseLockProperties.PREFIX + ".renew-interval("
                + properties.getRenewInterval() + ") 必须小于 ttl(" + properties.getTtl()
                + ")：否则锁会在第一次续期之前过期，多副本下会并发执行");
        }
        if (properties.getKeyPrefix() == null || properties.getKeyPrefix().isBlank()) {
            throw new IllegalStateException(LeaseLockProperties.PREFIX + ".key-prefix 不能为空");
        }
        log.info("[ypbin] 租约锁已启用：keyPrefix={} ttl={} renewInterval={} failOpen={} metricPrefix={}",
            properties.getKeyPrefix(), properties.getTtl(), properties.getRenewInterval(),
            properties.isFailOpen(), properties.getMetricPrefix());
    }

    /**
     * 续期看门狗调度器（始终装配，使内存兜底实现也能自动续期）。
     */
    @Bean
    @ConditionalOnMissingBean
    public LeaseRenewalScheduler leaseRenewalScheduler() {
        return new ScheduledLeaseRenewalScheduler();
    }

    /**
     * 进程内租约锁：兜底，仅当容器中不存在任何 {@link LeaseLockService} 时装配
     * （Redis 实现存在时即退让）。**只覆盖单实例**，多副本部署下跨实例互斥不生效，装配时打 WARN。
     */
    @Bean
    @ConditionalOnMissingBean(LeaseLockService.class)
    @ConditionalOnProperty(prefix = LeaseLockProperties.PREFIX, name = "enabled", havingValue = "true",
        matchIfMissing = true)
    public LeaseLockService inMemoryLeaseLockService() {
        log.warn("[ypbin] 未发现 StringRedisTemplate：租约锁退化为**进程内**实现，"
            + "多副本部署下跨实例互斥不生效（后台任务仍会在每个副本各跑一遍；单实例可用）");
        return LeaseLockServiceImpl.inMemory(properties);
    }

    /**
     * 空锁：{@code ypbin.lock.enabled=false} 时装配，语义为「每个副本都执行」（退回加锁之前的行为）。
     */
    @Bean
    @ConditionalOnMissingBean(LeaseLockService.class)
    @ConditionalOnProperty(prefix = LeaseLockProperties.PREFIX, name = "enabled", havingValue = "false")
    public LeaseLockService noopLeaseLockService() {
        return new NoopLeaseLockService();
    }

    /**
     * Redis 租约锁配置。类级 {@code @ConditionalOnClass(StringRedisTemplate.class)}：无 Redis 时
     * 整个类被跳过、其 {@code @Bean} 方法不被内省，从而外层配置在无 spring-data-redis 环境下也能正常启动。
     *
     * <p>类级 {@code @ConditionalOnClass} 只判断 classpath 是否有该类，不代表容器里真有可用的
     * {@link StringRedisTemplate} Bean（如未配置 Redis 连接、或测试环境只引入了依赖未装配自动配置）。
     * 叠加 {@code @ConditionalOnBean(StringRedisTemplate.class)}，两者都满足才展开本配置，避免 Redis Bean
     * 因缺少注入源在启动期抛 {@code UnsatisfiedDependencyException}。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = LeaseLockProperties.PREFIX, name = "enabled", havingValue = "true",
        matchIfMissing = true)
    static class RedisLeaseLockStoreConfiguration {

        /**
         * Redis 租约锁：多实例正确，存在 Redis 时优先装配。
         */
        @Bean
        @ConditionalOnMissingBean(LeaseLockService.class)
        public LeaseLockService redisLeaseLockService(StringRedisTemplate redisTemplate,
                                                     LeaseLockProperties properties) {
            return LeaseLockServiceImpl.redis(redisTemplate, properties);
        }
    }

    /**
     * 任务守卫（观测增强）。单独嵌套在 {@code @ConditionalOnClass(MeterRegistry.class)} 下：
     * micrometer 是可选依赖，无它时锁能力应照常可用，只是没有指标。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    static class LeaseLockGuardConfiguration {

        /**
         * 任务守卫：抢不到=跳过、存储异常按 {@code fail-open} 策略、finally 必释放、按任务计数。
         */
        @Bean
        @ConditionalOnMissingBean
        public LeaseLockGuard leaseLockGuard(LeaseLockService leaseLockService,
                                             LeaseRenewalScheduler leaseRenewalScheduler,
                                             LeaseLockProperties properties,
                                             MeterRegistry meterRegistry) {
            return new LeaseLockGuard(leaseLockService, leaseRenewalScheduler, properties, meterRegistry);
        }
    }
}

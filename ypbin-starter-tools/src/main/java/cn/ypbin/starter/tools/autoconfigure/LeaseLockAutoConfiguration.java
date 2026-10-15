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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
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
 * 续期看门狗调度器 {@link LeaseRenewalScheduler}，以及任务守卫 {@link LeaseLockGuard}。
 * <b>三个 Bean 都无条件装配</b>（仅受 {@code @ConditionalOnMissingBean} 约束，可被业务方覆盖）：
 * 宿主只要引入了本模块，注入 {@link LeaseLockGuard} 就一定能拿到——不存在「指标/存储没就绪 ⇒
 * 守卫整块消失 ⇒ 下游启动失败」的路径。</p>
 *
 * <p><strong>为什么这里必须有 {@code @AutoConfigureAfter}：</strong>{@code @ConditionalOnBean} 只看得见
 * <em>在它之前</em>已处理完的 Bean 定义。本模块包名 {@code cn.ypbin.*} 在 Boot 的自动配置排序中
 * 排在 {@code org.springframework.*} <b>之前</b>，因此若不加顺序约束，下面
 * {@link RedisLeaseLockStoreConfiguration} 的 {@code @ConditionalOnBean(StringRedisTemplate.class)}
 * 会在 Boot 的 {@code DataRedisAutoConfiguration} 注册模板之前被求值 ⇒ 判定为「没有 Redis」⇒
 * <b>静默退化为进程内锁</b>（多副本下互斥失效，且没有任何报错）。这是 2026-10-15 线上事故
 * （guard 缺失导致下游启动失败）暴露出的同一类陷阱，已在装配用例中用真实 Boot 自动配置组合钉死。</p>
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
@AutoConfigureAfter(name = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration")
@EnableConfigurationProperties(LeaseLockProperties.class)
@Import({LeaseLockAutoConfiguration.RedisLeaseLockStoreConfiguration.class,
    LeaseLockAutoConfiguration.LeaseLockGuardConfiguration.class})
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
     *
     * <p><b>顺序前提</b>：{@code @ConditionalOnBean} 的判定依赖处理顺序，故本类所在的自动配置用
     * 类级 {@code @AutoConfigureAfter(DataRedisAutoConfiguration)} 保证 Boot 先把
     * {@code StringRedisTemplate} 注册进来（否则本配置恒不展开、静默退化为进程内锁）。</p>
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
     * 任务守卫的装配。
     *
     * <p><b>类级条件只判 classpath（{@code @ConditionalOnClass(MeterRegistry.class)}），
     * 绝不判 Bean 是否存在</b>——这是 2026-10-15 线上事故的修复点：
     * 此前守卫放在 {@code @ConditionalOnBean(MeterRegistry.class)} 的嵌套配置里，而
     * {@code @ConditionalOnBean} 只看得见「先于它处理完」的 Bean 定义；一旦本模块的自动配置
     * 先于注册 {@code MeterRegistry} 的 Boot {@code MetricsAutoConfiguration} 被处理，
     * 守卫就整块消失 ⇒ 下游所有注入 {@link LeaseLockGuard} 的业务 Bean 以
     * {@code UnsatisfiedDependencyException} 启动失败（dev 环境实测）。</p>
     *
     * <p>类级 {@code @ConditionalOnClass} 仍然必要：{@link LeaseLockGuard} 的公开构造器签名含
     * {@code MeterRegistry}，micrometer 不在 classpath 时该类型无法加载。类级条件在方法内省
     * <em>之前</em>求值，故 micrometer 缺失时整个类被跳过、不会抛 {@code NoClassDefFoundError}。</p>
     *
     * <p>类内用 {@link ObjectProvider} 取 registry：**取不到就把指标降级为不记录**，锁语义完全不变。
     * 指标是可观测性增强，绝不能成为「锁能力可用」的必要条件——与 pom 里把 micrometer 声明为
     * optional 是同一口径。</p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    static class LeaseLockGuardConfiguration {

        /**
         * 任务守卫：抢不到=跳过、存储异常按 {@code fail-open} 策略、finally 必释放、按任务计数（有 registry 时）。
         */
        @Bean
        @ConditionalOnMissingBean
        public LeaseLockGuard leaseLockGuard(LeaseLockService leaseLockService,
                                             LeaseRenewalScheduler leaseRenewalScheduler,
                                             LeaseLockProperties properties,
                                             ObjectProvider<MeterRegistry> meterRegistryProvider) {
            MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
            if (meterRegistry == null) {
                log.warn("[ypbin] 容器中未发现 MeterRegistry：任务守卫照常装配，但**不记录** "
                    + "{}.skipped/.unavailable/.renew.failed 指标（锁语义不受影响）",
                    properties.getMetricPrefix());
            }
            return new LeaseLockGuard(leaseLockService, leaseRenewalScheduler, properties, meterRegistry);
        }
    }
}

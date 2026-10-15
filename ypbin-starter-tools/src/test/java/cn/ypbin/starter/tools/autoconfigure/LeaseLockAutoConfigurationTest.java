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

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.tools.lock.LeaseLockGuard;
import cn.ypbin.starter.tools.lock.LeaseLockProperties;
import cn.ypbin.starter.tools.lock.LeaseLockService;
import cn.ypbin.starter.tools.lock.LeaseLockServiceImpl;
import cn.ypbin.starter.tools.lock.NoopLeaseLockService;
import cn.ypbin.starter.tools.lock.ScheduledLeaseRenewalScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link LeaseLockAutoConfiguration} 的装配门禁。
 *
 * <p>这个类存在的直接理由是一次**线上事故**：{@code LeaseLockGuard} 被放在
 * {@code @ConditionalOnBean(MeterRegistry.class)} 的嵌套配置里，而 {@code @ConditionalOnBean}
 * 只看得见「先于它处理完」的 Bean 定义。本模块自动配置一旦先于 Boot 的
 * {@code MetricsAutoConfiguration} 被处理，守卫就整块消失，下游注入它的业务 Bean 全部以
 * {@code UnsatisfiedDependencyException} 启动失败。当时 tools 模块单测与全量编译**都是绿的**——
 * 因为没有任何一条用例断言过「守卫必须存在」。</p>
 *
 * <p>因此本类钉住四条（都是「配错了看不出来」的形态）：</p>
 * <ol>
 *   <li><b>无 micrometer（无 registry）时守卫依然存在且可执行</b>——事故的直接反例；</li>
 *   <li><b>有 registry 时同样装配</b>，且计数真的落到 registry 上；</li>
 *   <li><b>无 Redis / 无 micrometer 都能启动</b>（可选依赖不得成为必需条件）；</li>
 *   <li><b>真实 Boot 自动配置组合下 Redis 分支被正确选中</b>——防「静默退化为进程内锁」
 *       （同一类顺序陷阱），故用例里带上 {@link DataRedisAutoConfiguration} 而不是只放一个
 *       用户自定义模板 Bean。</li>
 * </ol>
 *
 * @author wenbin
 * @since 2026-10-15
 */
class LeaseLockAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(LeaseLockAutoConfiguration.class));

    @Test
    @DisplayName("★ 无 MeterRegistry：守卫仍必须装配且能跑任务（线上事故的直接反例）")
    void guardMustExistWithoutMeterRegistry() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context)
                .as("容器里没有 MeterRegistry 时守卫也必须存在：否则下游注入它的业务 Bean 直接启动失败"
                    + "（dev 环境 UnsatisfiedDependencyException 的根因）")
                .hasSingleBean(LeaseLockGuard.class);

            boolean executed = context.getBean(LeaseLockGuard.class)
                .runExclusively("yake-task", () -> { });
            assertThat(executed)
                .as("无指标时锁语义必须完全不变：抢到锁就执行")
                .isTrue();
        });
    }

    @Test
    @DisplayName("★ 有 MeterRegistry：守卫装配，且跳过计数落到 registry 上")
    void guardMustCountWhenMeterRegistryPresent() {
        runner.withUserConfiguration(MeterRegistryConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(LeaseLockGuard.class);
            assertThat(context.getBean(LeaseLockProperties.class).getMetricPrefix())
                .as("指标前缀来自配置，默认不含宿主应用域名")
                .isEqualTo("ypbin.lock");

            // 用一个「永远抢不到」的锁服务触发 skipped 计数，证明指标确实接了 registry
            LeaseLockGuard guard = guardOverBusyLock(context);
            assertThat(guard.runExclusively("busy-task", () -> { })).isFalse();
            MeterRegistry registry = context.getBean(MeterRegistry.class);
            assertThat(registry.find("ypbin.lock.skipped").tag("task", "busy-task").counter())
                .as("有 registry 时跳过必须计数")
                .isNotNull();
        });
    }

    @Test
    @DisplayName("无 Redis + 无 micrometer：进程内实现兜底并正常启动（锁能力不依赖可选依赖）")
    void shouldStartWithoutRedisAndWithoutMicrometer() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(LeaseLockService.class);
            assertThat(context.getBean(LeaseLockService.class))
                .as("无 Redis 时必须退化为进程内实现，而不是启动失败")
                .isInstanceOf(LeaseLockServiceImpl.class);
            assertThat(context).hasSingleBean(ScheduledLeaseRenewalScheduler.class);
        });
    }

    @Test
    @DisplayName("★ 真实 Boot 自动配置组合：有 Redis 时选 Redis 实现（防静默退化为进程内锁）")
    void shouldSelectRedisUnderRealBootAutoConfiguration() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                DataRedisAutoConfiguration.class, LeaseLockAutoConfiguration.class))
            .withUserConfiguration(RedisConnectionFactoryConfiguration.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.containsBean("stringRedisTemplate"))
                    .as("前置条件：Boot 的 DataRedisAutoConfiguration 已注册模板")
                    .isTrue();
                assertThat(context.getBeanNamesForType(LeaseLockService.class))
                    .as("有 Redis 时必须装配 Redis 实现而不是内存兜底：本模块包名在 Boot 排序中靠前，"
                        + "缺 @AutoConfigureAfter 会让 @ConditionalOnBean 判定为「没有 Redis」而静默退化"
                        + "（多副本互斥失效且无任何报错）")
                    .hasSize(1);
                assertThat(context.getBean(LeaseLockService.class)).isNotInstanceOf(NoopLeaseLockService.class);
                assertThat(context.containsBean("redisLeaseLockService"))
                    .as("应选中 redisLeaseLockService 这个 Bean")
                    .isTrue();
            });
    }

    @Test
    @DisplayName("enabled=false：装配空锁（退回「每个副本都执行」），不抛异常")
    void disabledShouldRegisterNoopLock() {
        runner.withPropertyValues("ypbin.lock.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(LeaseLockService.class)).isInstanceOf(NoopLeaseLockService.class);
        });
    }

    @Test
    @DisplayName("启动自检：非法配置（renew-interval >= ttl）在上下文刷新期即失败")
    void invalidConfigShouldFailFastAtStartup() {
        runner.withPropertyValues("ypbin.lock.ttl=10s", "ypbin.lock.renew-interval=10s").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                .hasRootCauseMessage("ypbin.lock.renew-interval(PT10S) 必须小于 ttl(PT10S)："
                    + "否则锁会在第一次续期之前过期，多副本下会并发执行");
        });
    }

    @Test
    @DisplayName("业务方自定义 LeaseLockService 时默认实现退让（可覆盖）")
    void userBeanShouldOverrideDefaults() {
        runner.withUserConfiguration(UserLeaseLockConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(LeaseLockService.class);
            assertThat(context.getBean(LeaseLockService.class)).isInstanceOf(NoopLeaseLockService.class);
            assertThat(context.getBeanNamesForType(LeaseLockService.class))
                .as("只应有一个 LeaseLockService Bean（自定义覆盖默认）")
                .hasSize(1);
        });
    }

    @Test
    @DisplayName("业务方自定义 LeaseLockGuard 时默认实现退让（可覆盖）")
    void userGuardBeanShouldOverrideDefault() {
        runner.withUserConfiguration(UserGuardConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(LeaseLockGuard.class);
            assertThat(context.getBeanNamesForType(LeaseLockGuard.class))
                .as("自定义守卫必须覆盖默认实现，且只留一个")
                .hasSize(1);
        });
    }

    /**
     * 造一个底层锁永远抢不到的守卫（用容器里的 registry），用于验证 skipped 计数。
     *
     * @param context 上下文
     * @return 守卫
     */
    private static LeaseLockGuard guardOverBusyLock(
        org.springframework.context.ApplicationContext context) {
        LeaseLockProperties properties = context.getBean(LeaseLockProperties.class);
        LeaseLockService busy = new LeaseLockService() {
            @Override
            public cn.ypbin.starter.tools.lock.LockLease tryAcquire(String lockKey) {
                return null;
            }
        };
        return new LeaseLockGuard(busy, context.getBean(ScheduledLeaseRenewalScheduler.class),
            properties, context.getBean(MeterRegistry.class));
    }

    /**
     * 假 Redis 模板：用 Mockito 替身而非 {@code new StringRedisTemplate()}——后者需要
     * {@code RedisConnectionFactory}，在本测试里既不必要也会把「装配选择」的断言变成「能否连上 Redis」。
     */
    @Configuration(proxyBeanMethods = false)
    static class FakeRedisConfiguration {

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return Mockito.mock(StringRedisTemplate.class);
        }
    }

    /**
     * 真实 Boot Redis 自动配置所需的连接工厂（mock 即可，装配阶段不建连）。
     */
    @Configuration(proxyBeanMethods = false)
    static class RedisConnectionFactoryConfiguration {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return Mockito.mock(RedisConnectionFactory.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MeterRegistryConfiguration {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserLeaseLockConfiguration {

        @Bean
        LeaseLockService customLeaseLockService() {
            return new NoopLeaseLockService();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserGuardConfiguration {

        @Bean
        LeaseLockGuard customLeaseLockGuard(LeaseLockService service,
                                            cn.ypbin.starter.tools.lock.LeaseRenewalScheduler scheduler,
                                            LeaseLockProperties properties) {
            return new LeaseLockGuard(service, scheduler, properties);
        }
    }
}

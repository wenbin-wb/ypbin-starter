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
import cn.ypbin.starter.tools.lock.NoopLeaseLockService;
import cn.ypbin.starter.tools.lock.ScheduledLeaseRenewalScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link LeaseLockAutoConfiguration} 的装配门禁。
 *
 * <p>钉住三件事（都是「配错了看不出来」的形态）：</p>
 * <ol>
 *   <li><b>无 Redis 也能启动</b> ⇒ 落到进程内兜底（若 Redis 相关 Bean 被方法内省，会抛
 *       {@code NoClassDefFoundError}，启动直接失败）；</li>
 *   <li><b>无 micrometer 也能启动</b> ⇒ 锁照常可用，只是没有指标（不能把可选依赖变成硬前置）；</li>
 *   <li><b>所有能力 Bean 可被业务方覆盖</b>（{@code @ConditionalOnMissingBean}）。</li>
 * </ol>
 *
 * <p>有 Redis 的分支（{@code RedisLeaseLockStore}）用 {@code @ConditionalOnBean} 要求容器里真有
 * {@link StringRedisTemplate} 实例，本测试通过一个假 Bean 触发，验证装配选择的是 Redis 实现。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
class LeaseLockAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(LeaseLockAutoConfiguration.class));

    @Test
    @DisplayName("无 Redis + 无 micrometer：落到进程内实现并正常启动（锁能力不依赖可选依赖）")
    void shouldStartWithoutRedisAndWithoutMicrometer() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(LeaseLockService.class);
            assertThat(context.getBean(LeaseLockService.class))
                .as("无 Redis 时必须退化为进程内实现，而不是启动失败")
                .isNotInstanceOf(NoopLeaseLockService.class);
            assertThat(context).hasSingleBean(ScheduledLeaseRenewalScheduler.class);
            assertThat(context).doesNotHaveBean(LeaseLockGuard.class);
        });
    }

    @Test
    @DisplayName("有 Redis：装配 Redis 租约锁实现（多实例正确）")
    void shouldPreferRedisStoreWhenTemplatePresent() {
        runner.withUserConfiguration(FakeRedisConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(LeaseLockService.class);
            // 无 micrometer 时守卫不装配，但锁能力本身必须可用
            assertThat(context.getBean(LeaseLockService.class)).isNotNull();
        });
    }

    @Test
    @DisplayName("有 micrometer：任务守卫自动装配（按任务计数）")
    void shouldRegisterGuardWhenMeterRegistryPresent() {
        runner.withUserConfiguration(MeterRegistryConfiguration.class).run(context -> {
            assertThat(context).hasSingleBean(LeaseLockGuard.class);
            assertThat(context.getBean(LeaseLockProperties.class).getMetricPrefix())
                .as("指标前缀来自配置，默认不含宿主应用域名")
                .isEqualTo("ypbin.lock");
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
}

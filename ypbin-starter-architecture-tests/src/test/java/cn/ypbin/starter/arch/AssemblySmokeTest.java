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
package cn.ypbin.starter.arch;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.messaging.autoconfigure.SseAutoConfiguration;
import cn.ypbin.starter.messaging.sse.RedisSseTicketStore;
import cn.ypbin.starter.messaging.sse.SseEmitterManager;
import cn.ypbin.starter.messaging.sse.SseFanoutBridge;
import cn.ypbin.starter.messaging.sse.SseTicketStore;
import cn.ypbin.starter.security.autoconfigure.SecurityAutoConfiguration;
import cn.ypbin.starter.security.password.lock.PasswordAttemptStore;
import cn.ypbin.starter.security.password.lock.RedisPasswordAttemptStore;
import cn.ypbin.starter.sign.autoconfigure.SignAutoConfiguration;
import cn.ypbin.starter.sign.core.NonceStore;
import cn.ypbin.starter.sign.core.RedisNonceStore;
import cn.ypbin.starter.tools.autoconfigure.LeaseLockAutoConfiguration;
import cn.ypbin.starter.tools.autoconfigure.ToolsAutoConfiguration;
import cn.ypbin.starter.tools.idempotent.IdempotentStore;
import cn.ypbin.starter.tools.idempotent.InMemoryIdempotentStore;
import cn.ypbin.starter.tools.idempotent.RedisIdempotentStore;
import cn.ypbin.starter.tools.limiter.InMemoryRateLimiterStore;
import cn.ypbin.starter.tools.limiter.RateLimiterStore;
import cn.ypbin.starter.tools.limiter.RedisRateLimiterStore;
import cn.ypbin.starter.tools.lock.InMemoryLockService;
import cn.ypbin.starter.tools.lock.LeaseLockGuard;
import cn.ypbin.starter.tools.lock.LeaseLockService;
import cn.ypbin.starter.tools.lock.LockService;
import cn.ypbin.starter.tools.lock.RedisLockService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * <b>真实 Boot 自动配置组合</b>的装配冒烟（跨模块，防「单模块用例全绿但生产起不来」）。
 *
 * <p><b>为什么必须有这一层</b>：2026-10-15 线上事故（{@code LeaseLockGuard} 缺失 ⇒ 下游
 * {@code UnsatisfiedDependencyException} 启动失败）在<em>编译期、各模块单测、全量编译</em>里
 * <b>全部是绿的</b>。原因是缺陷只在「多个自动配置按 Boot 的真实顺序共存」时才成立：</p>
 *
 * <ul>
 *   <li>{@code @ConditionalOnBean(X)} 只看得见<em>先于它处理完</em>的 Bean 定义；</li>
 *   <li>本仓包名 {@code cn.ypbin.*} 在 Boot 自动配置排序中排在 {@code org.springframework.*}
 *       <b>之前</b>（无显式顺序时按类名字典序）；</li>
 *   <li>而 X（{@code StringRedisTemplate} / {@code MeterRegistry}）恰恰由 Boot 的
 *       {@code DataRedisAutoConfiguration} / {@code MetricsAutoConfiguration} 提供；</li>
 *   <li>⇒ 条件在 Boot 注册它们<em>之前</em>求值 ⇒ <b>恒假</b> ⇒ 静默退化为内存实现，
 *       或（对守卫而言）Bean 整块消失。</li>
 * </ul>
 *
 * <p>因此本类刻意<b>同时</b>装配 Boot 的 Redis 自动配置与 starter 各模块的自动配置，
 * 断言「该用 Redis 的必须真的是 Redis」，而不是「有模板 Bean 时选了 Redis」——后者用一个用户自定义
 * 模板 Bean 就能测出来，测不出顺序问题。</p>
 *
 * <p>本类还覆盖一条相反的可用性要求：<b>没有</b> {@code MeterRegistry} 时守卫仍必须存在且能跑任务
 * （指标是可选能力，不得成为使用锁的前置条件）。</p>
 *
 * @author wenbin
 * @since 2026-10-15
 */
class AssemblySmokeTest {

    /**
     * 一个真实 Boot 自动配置组合：DataRedis（提供模板）+ 四个 starter 自动配置。
     */
    private WebApplicationContextRunner redisRunner() {
        return new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                DataRedisAutoConfiguration.class,
                ToolsAutoConfiguration.class,
                LeaseLockAutoConfiguration.class,
                SignAutoConfiguration.class,
                SecurityAutoConfiguration.class,
                SseAutoConfiguration.class))
            .withPropertyValues(
                "ypbin.sign.enabled=true",
                "ypbin.sse.enabled=true",
                "ypbin.security.enabled=true",
                "ypbin.tools.rate-limit.enabled=true")
            .withUserConfiguration(RedisStubsConfiguration.class);
    }

    @Test
    @DisplayName("★ 有 Redis：租约锁/守卫/限流/幂等/分布式锁/SSE 票据与扇出/nonce/密码计数 全部走 Redis 实现")
    void withRedis_everyRedisBackedCollaboratorMustBeRedis() {
        redisRunner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.containsBean("stringRedisTemplate"))
                .as("前置条件：Boot 的 DataRedisAutoConfiguration 已注册 StringRedisTemplate")
                .isTrue();

            // ① 租约锁：此前静默退化为进程内（多副本互斥失效，无任何报错）
            assertThat(context.containsBean("redisLeaseLockService"))
                .as("LeaseLockService 必须选中 redisLeaseLockService")
                .isTrue();
            assertThat(context.getBean(LeaseLockService.class)).isNotNull();

            // ② 守卫：线上事故的直接观测点（必须存在，且真的能抢到锁并执行）
            assertThat(context).hasSingleBean(LeaseLockGuard.class);
            // 用 callExclusively 而不是只断言布尔值：executed=true 且 degraded=false 才证明
            // 「Redis 锁真的抢到了」，而不是走 fail-open 降级路径蒙混过关
            var outcome = context.getBean(LeaseLockGuard.class)
                .callExclusively("smoke-task", () -> Boolean.TRUE);
            assertThat(outcome.executed())
                .as("守卫必须执行任务")
                .isTrue();
            assertThat(outcome.degraded())
                .as("degraded 必须为 false：走的是真实 Redis 抢锁路径，不是 fail-open 降级")
                .isFalse();

            // ③ tools 三件存储
            assertThat(context.getBean(RateLimiterStore.class)).isInstanceOf(RedisRateLimiterStore.class);
            assertThat(context.getBean(IdempotentStore.class)).isInstanceOf(RedisIdempotentStore.class);
            assertThat(context.getBean(LockService.class)).isInstanceOf(RedisLockService.class);

            // ④ SSE：票据 + 扇出桥（缺桥 ⇒ 多副本只推本实例连接，跨实例推送静默丢失）
            assertThat(context.getBean(SseTicketStore.class)).isInstanceOf(RedisSseTicketStore.class);
            assertThat(context.getBeanNamesForType(SseFanoutBridge.class))
                .as("有 Redis 时必须装配扇出桥")
                .isNotEmpty();
            assertThat(context).hasSingleBean(SseEmitterManager.class);

            // ⑤ sign / security 的 Redis 存储
            assertThat(context.getBean(NonceStore.class)).isInstanceOf(RedisNonceStore.class);
            assertThat(context.getBean(PasswordAttemptStore.class))
                .isInstanceOf(RedisPasswordAttemptStore.class);
        });
    }

    @Test
    @DisplayName("★ 无 MeterRegistry：LeaseLockGuard 仍必须存在且能跑任务（指标是可选能力）")
    void withoutMeterRegistry_guardMustStillExistAndRun() {
        // 刻意只装配租约锁自动配置、且不提供任何 MeterRegistry Bean
        new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LeaseLockAutoConfiguration.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context)
                    .as("无 MeterRegistry 时守卫必须装配：否则下游注入它的业务 Bean 启动即失败"
                        + "（dev 环境 UnsatisfiedDependencyException 的根因）")
                    .hasSingleBean(LeaseLockGuard.class);
                assertThat(context.getBean(LeaseLockGuard.class).runExclusively("no-metrics", () -> { }))
                    .as("无指标时锁语义不得改变：抢到锁就执行")
                    .isTrue();
                assertThat(context).doesNotHaveBean(MeterRegistry.class);
            });
    }

    @Test
    @DisplayName("★ 无 Redis：全部退回内存实现并正常启动（可用性优先，不报错）")
    void withoutRedis_fallsBackToInMemoryAndStarts() {
        new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                ToolsAutoConfiguration.class,
                LeaseLockAutoConfiguration.class,
                SignAutoConfiguration.class,
                SecurityAutoConfiguration.class))
            .withPropertyValues(
                "ypbin.sign.enabled=true",
                "ypbin.security.enabled=true",
                "ypbin.tools.rate-limit.enabled=true")
            .withUserConfiguration(MeterRegistryConfiguration.class)
            .run(context -> {
                assertThat(context)
                    .as("无 Redis 时必须能正常启动（不得因缺少可选依赖而失败）")
                    .hasNotFailed();
                assertThat(context.containsBean("stringRedisTemplate")).isFalse();

                assertThat(context.getBean(RateLimiterStore.class)).isInstanceOf(InMemoryRateLimiterStore.class);
                assertThat(context.getBean(IdempotentStore.class)).isInstanceOf(InMemoryIdempotentStore.class);
                assertThat(context.getBean(LockService.class)).isInstanceOf(InMemoryLockService.class);
                assertThat(context.containsBean("inMemoryLeaseLockService"))
                    .as("无 Redis 时租约锁退化为进程内实现")
                    .isTrue();
                assertThat(context.getBean(NonceStore.class))
                    .isInstanceOf(cn.ypbin.starter.sign.core.InMemoryNonceStore.class);
                assertThat(context.getBean(PasswordAttemptStore.class))
                    .isInstanceOf(cn.ypbin.starter.security.password.lock.InMemoryPasswordAttemptStore.class);
                // 有 MeterRegistry 且锁可用 ⇒ 守卫仍在，且能执行
                assertThat(context).hasSingleBean(LeaseLockGuard.class);
                assertThat(context.getBean(LeaseLockGuard.class).runExclusively("fallback", () -> { })).isTrue();
            });
    }

    /**
     * Redis 桩：连接工厂 + 订阅容器 + **执行成功的模板替身**。
     *
     * <p>模板替身必须让三条 Lua 调用返回「成功」（{@code 1}），否则守卫会走 fail-open 降级路径，
     * 断言就变成「降级也算过」——那正是本冒烟要避免的假绿。替身让抢锁/续期/释放都真实成功，
     * 从而证明「选中的是 Redis 实现且功能可用」。</p>
     *
     * <p>订阅容器用桩：真实容器在假连接上 {@code start()} 会订阅超时，把上下文带崩。</p>
     */
    @Configuration(proxyBeanMethods = false)
    static class RedisStubsConfiguration {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return Mockito.mock(RedisConnectionFactory.class);
        }

        @Bean
        RedisMessageListenerContainer redisMessageListenerContainer() {
            return Mockito.mock(RedisMessageListenerContainer.class);
        }

        /**
         * 模板替身：Lua 执行成功（返回 1L ⇒ 抢锁/续期/释放都成功）。
         * Boot 的 {@code DataRedisAutoConfiguration} 带 {@code @ConditionalOnMissingBean}，
         * 故本 Bean 会取代它注册的模板，而「模板存在」这一前置条件依旧成立。
         */
        @Bean
        StringRedisTemplate stringRedisTemplate() {
            StringRedisTemplate template = Mockito.mock(StringRedisTemplate.class);
            Mockito.when(template.execute(Mockito.any(org.springframework.data.redis.core.script.RedisScript.class),
                Mockito.anyList(), Mockito.any(Object[].class))).thenReturn(1L);
            return template;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MeterRegistryConfiguration {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}

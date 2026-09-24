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
package cn.ypbin.starter.security.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.ypbin.starter.security.identity.IdentityStpLogic;
import cn.ypbin.starter.security.password.lock.InMemoryPasswordAttemptStore;
import cn.ypbin.starter.security.password.lock.PasswordAttemptStore;
import cn.ypbin.starter.security.password.lock.RedisPasswordAttemptStore;
import cn.ypbin.starter.security.satoken.SaTokenWebConfigurer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link SecurityAutoConfiguration} 装配测试。
 *
 * <p>锁定密码错误计数存储「可选 Redis 依赖」的两种场景，防回归：</p>
 * <ul>
 *     <li><strong>无 Redis</strong>：用 {@link FilteredClassLoader} 隐藏 {@link StringRedisTemplate}，复现轻量消费端。
 *     配置类内省不得触碰 Redis 类型（否则 {@code NoClassDefFoundError} 启动即崩），且装配内存实现。</li>
 *     <li><strong>有 Redis</strong>：提供 {@link StringRedisTemplate} Bean，装配的应是 Redis 实现而非内存兜底
 *     （验证 {@code @Import} 保证 Redis 先于内存兜底注册）。</li>
 * </ul>
 *
 * @author wenbin
 * @since 2026-08-07
 */
class SecurityAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class));

    @Test
    void withoutRedis_startsAndUsesInMemoryStore() {
        runner.withClassLoader(new FilteredClassLoader(StringRedisTemplate.class))
            .run(context -> {
                // 关键：无 Redis 时上下文能正常启动（不因配置类内省触碰 StringRedisTemplate 而崩）
                assertThat(context).hasNotFailed();
                assertThat(context).getBean(PasswordAttemptStore.class).isInstanceOf(InMemoryPasswordAttemptStore.class);
            });
    }

    @Test
    void withRedis_usesRedisStore() {
        runner.withUserConfiguration(RedisTemplateConfig.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                // @Import 保证 Redis 实现先注册，内存兜底的 @ConditionalOnMissingBean 退让
                assertThat(context).getBean(PasswordAttemptStore.class).isInstanceOf(RedisPasswordAttemptStore.class);
            });
    }

    @Test
    void interceptorOff_butAnnotationCheckOn_stillRegistersConfigurer() {
        runner.withPropertyValues("ypbin.security.interceptor=false")
            .run(context -> {
                assertThat(context).hasNotFailed();
                // 注解鉴权仍开启 ⇒ 必须装配拦截器配置（否则下游的 @SaCheckPermission 又会静默失效）
                assertThat(context).hasSingleBean(SaTokenWebConfigurer.class);
            });
    }

    @Test
    void bothSwitchesOff_stillRegistersConfigurerButNoInterceptor() {
        runner.withPropertyValues("ypbin.security.interceptor=false", "ypbin.security.annotation-check=false")
            .run(context -> {
                assertThat(context).hasNotFailed();
                // 装配与否不再由开关决定（@ConditionalOnExpression 对 yes/1/on 会让应用启动失败）；
                // 「两个开关都关 ⇒ 不注册任何拦截器」由 SaTokenWebConfigurerTest 行为断言
                assertThat(context).hasSingleBean(SaTokenWebConfigurer.class);
            });
    }

    @Test
    void nonBooleanSwitchValues_doNotBreakStartup() {
        for (String value : List.of("yes", "on", "1")) {
            runner.withPropertyValues("ypbin.security.interceptor=" + value)
                .run(context -> {
                    assertThat(context).as("interceptor=%s 不应导致启动失败", value).hasNotFailed();
                    assertThat(context).hasSingleBean(SaTokenWebConfigurer.class);
                });
        }
    }

    @Test
    void identityEnabled_registersIdentityStpLogic() {
        StpLogic original = StpUtil.stpLogic;
        try {
            runner.withPropertyValues("ypbin.security.identity.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IdentityStpLogic.class);
                    // 显式注册进 StpUtil 是注解鉴权能否生效的前提，不能只依赖 Sa-Token 的 Bean 自动注入
                    assertThat(StpUtil.stpLogic).isInstanceOf(IdentityStpLogic.class);
                });
        } finally {
            StpUtil.setStpLogic(original);
        }
    }

    @Test
    void identityDisabled_keepsDefaultStpLogic() {
        StpLogic original = StpUtil.stpLogic;
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(IdentityStpLogic.class);
            assertThat(StpUtil.stpLogic).isSameAs(original);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class RedisTemplateConfig {
        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return Mockito.mock(StringRedisTemplate.class);
        }
    }
}

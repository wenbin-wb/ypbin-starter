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
package cn.ypbin.starter.tenant.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.security.core.LoginUser;
import cn.ypbin.starter.security.identity.IdentityContext;
import cn.ypbin.starter.tenant.core.TenantProvider;
import cn.ypbin.starter.tenant.provider.IdentityTenantProvider;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 身份租户桥接自动装配测试。
 *
 * <p>覆盖五类证据：提供者本身生效、<b>默认空租户提供者正确退让</b>（并含顺序注解有效性的正反对照）、
 * 业务自定义 Bean 优先、开关关闭、security 缺省时的行为不变。</p>
 *
 * @author wenbin
 * @since 2026-10-17
 */
class IdentityTenantAutoConfigurationTest {

    /** 桥接提供者 bean 名（{@code @Bean} 方法名） */
    private static final String IDENTITY_PROVIDER_BEAN = "identityTenantProvider";

    /** 默认空租户提供者 bean 名（{@link TenantAutoConfiguration#tenantProvider()}） */
    private static final String DEFAULT_PROVIDER_BEAN = "tenantProvider";

    private static final long IDENTITY_TENANT_ID = 7L;

    private static final long ORDERING_TENANT_ID = 9L;

    private static final long CUSTOM_TENANT_ID = 42L;

    /**
     * {@link IdentityContext} 是 ThreadLocal 语义，必须逐用例清理，避免污染其它用例。
     */
    @AfterEach
    void clearIdentityContext() {
        IdentityContext.clear();
    }

    @Test
    @DisplayName("有身份且带租户：桥接提供者生效并返回该租户")
    void shouldWireIdentityProviderWhenIdentityPresent() {
        identityOnlyRunner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(TenantProvider.class);
            assertThat(context.getBean(TenantProvider.class)).isInstanceOf(IdentityTenantProvider.class);

            IdentityContext.setLoginUser(loginUser(IDENTITY_TENANT_ID));
            assertThat(context.getBean(TenantProvider.class).getCurrentTenantId()).contains(IDENTITY_TENANT_ID);
        });
    }

    @Test
    @DisplayName("无身份 / 身份未携带租户：桥接提供者返回 empty（不追加租户条件由下游 handler 决定）")
    void shouldReturnEmptyWithoutIdentityOrTenant() {
        identityOnlyRunner().run(context -> {
            TenantProvider provider = context.getBean(TenantProvider.class);

            assertThat(provider.getCurrentTenantId())
                .as("无身份时必须为空")
                .isEmpty();

            IdentityContext.setLoginUser(loginUser(null));
            assertThat(provider.getCurrentTenantId())
                .as("身份未携带租户时必须为空，不得猜测兜底租户")
                .isEmpty();
        });
    }

    @Test
    @DisplayName("★ 顺序证据：与默认配置同场时最终 TenantProvider 的实现类必须是 IdentityTenantProvider")
    void defaultProviderMustBackOffInFavorOfIdentityProvider() {
        fullRunner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.containsBean(DEFAULT_PROVIDER_BEAN))
                .as("默认空租户提供者必须因 @ConditionalOnMissingBean 退让，不得注册")
                .isFalse();

            TenantProvider provider = context.getBean(TenantProvider.class);
            assertThat(provider)
                .as("最终 Bean 的实现类必须是桥接实现；若为默认 lambda，说明桥接没能先于默认实现注册")
                .isInstanceOf(IdentityTenantProvider.class);

            IdentityContext.setLoginUser(loginUser(ORDERING_TENANT_ID));
            assertThat(provider.getCurrentTenantId()).contains(ORDERING_TENANT_ID);
        });
    }

    @Test
    @DisplayName("业务自定义 TenantProvider 始终优先，桥接实现不得覆盖")
    void customTenantProviderMustWin() {
        fullRunner()
            .withUserConfiguration(CustomTenantProviderConfiguration.class)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(IDENTITY_PROVIDER_BEAN);
                assertThat(context.getBean(TenantProvider.class).getCurrentTenantId()).contains(CUSTOM_TENANT_ID);
            });
    }

    @Test
    @DisplayName("ypbin.tenant.enabled=false：租户特性关闭时零 TenantProvider Bean（桥接不得残留）")
    void tenantDisabledRegistersNoProvider() {
        fullRunner()
            .withPropertyValues("ypbin.tenant.enabled=false")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(IDENTITY_PROVIDER_BEAN);
                // 决定性证据：租户特性关闭 = 桥接与默认实现都不得注册（避免"特性关了却多出 Bean"）
                assertThat(context).doesNotHaveBean(TenantProvider.class);
            });
    }

    @Test
    @DisplayName("security 不在 classpath：整个配置类跳过，不注册任何 TenantProvider（既有行为不变）")
    void shouldSkipEntirelyWhenIdentityContextAbsent() {
        identityOnlyRunner()
            .withClassLoader(new FilteredClassLoader(IdentityContext.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(TenantProvider.class);
            });
    }

    @Test
    @DisplayName("★ 顺序注解有效性（正向对照）：类名字典序不利时 @AutoConfigureBefore 仍让默认实现退让")
    void autoConfigureBeforeMustWinEvenWhenClassNameSortsAfterTenant() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                TenantAutoConfiguration.class, ZzzTenantProviderOrderingFixtures.Ordered.class))
            .withPropertyValues("ypbin.tenant.enabled=true")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.containsBean(DEFAULT_PROVIDER_BEAN)).isFalse();
                TenantProvider provider = context.getBean(TenantProvider.class);
                assertThat(provider).isInstanceOf(ZzzTenantProviderOrderingFixtures.MarkerTenantProvider.class);
                assertThat(provider.getCurrentTenantId())
                    .contains(ZzzTenantProviderOrderingFixtures.MARKER_TENANT_ID);
            });
    }

    @Test
    @DisplayName("★ 顺序注解有效性（反向对照）：去掉 @AutoConfigureBefore 后默认实现抢先注册，证明上条非字典序巧合")
    void withoutAutoConfigureBeforeDefaultProviderWinsWhenClassNameSortsAfterTenant() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                TenantAutoConfiguration.class, ZzzTenantProviderOrderingFixtures.Unordered.class))
            .withPropertyValues("ypbin.tenant.enabled=true")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.containsBean(DEFAULT_PROVIDER_BEAN))
                    .as("类名排在 TenantAutoConfiguration 之后且无顺序注解时，默认实现会先注册——"
                        + "这正说明生产实现的 @AutoConfigureBefore 是必需的，而非靠字典序侥幸")
                    .isTrue();
                assertThat(context.getBean(TenantProvider.class))
                    .isNotInstanceOf(ZzzTenantProviderOrderingFixtures.MarkerTenantProvider.class);
            });
    }

    /**
     * 只装配桥接配置：验证提供者本身，不受默认提供者是否在场影响。
     */
    private static ApplicationContextRunner identityOnlyRunner() {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdentityTenantAutoConfiguration.class))
            .withPropertyValues("ypbin.tenant.enabled=true");
    }

    /**
     * 桥接 + 默认配置同场。刻意把 {@link TenantAutoConfiguration} 放在数组<b>前面</b>（与「先注册者胜」的直觉
     * 相反）：若最终仍由桥接实现胜出，至少证明顺序不取决于入参数组顺序；注解与类名字典序孰为原因，
     * 由 {@link ZzzTenantProviderOrderingFixtures} 的正反对照用例区分。
     */
    private static ApplicationContextRunner fullRunner() {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                TenantAutoConfiguration.class, IdentityTenantAutoConfiguration.class))
            .withPropertyValues("ypbin.tenant.enabled=true");
    }

    private static LoginUser loginUser(Long tenantId) {
        LoginUser user = new LoginUser();
        user.setId(1L);
        user.setTenantId(tenantId);
        return user;
    }

    /**
     * 业务方自定义租户提供者：应始终优先于桥接实现。
     */
    @Configuration(proxyBeanMethods = false)
    static class CustomTenantProviderConfiguration {

        @Bean
        TenantProvider customTenantProvider() {
            return () -> Optional.of(CUSTOM_TENANT_ID);
        }
    }
}

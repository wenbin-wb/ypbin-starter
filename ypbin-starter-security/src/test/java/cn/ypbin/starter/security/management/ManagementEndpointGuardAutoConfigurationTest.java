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
package cn.ypbin.starter.security.management;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.security.core.PermissionProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

/**
 * {@link ManagementEndpointGuardAutoConfiguration} 装配测试。
 *
 * <p>锁定一条曾导致<b>启动崩溃</b>的配置组合：宿主关掉安全模块（{@code ypbin.security.enabled=false}）
 * 却打开管理端点收口（{@code ypbin.security.management.guard-enabled=true}）。此时
 * {@link PermissionProvider} 不存在（它只由被关掉的 {@code SecurityAutoConfiguration} 提供），
 * 若把它写成必需入参就会抛 {@code UnsatisfiedDependencyException}；正确行为是退化为空权限数据源
 * （一律拒绝，fail-closed）并让应用正常启动。</p>
 *
 * @author wenbin
 * @since 2026-11-06
 */
class ManagementEndpointGuardAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ManagementEndpointGuardAutoConfiguration.class));

    @Test
    @DisplayName("★ 缺 PermissionProvider（security.enabled=false 的等价状态）：不得启动失败，守卫按空权限 fail-closed 装配")
    void withoutPermissionProvider_startsAndGuardsFailClosed() {
        runner.withPropertyValues("ypbin.security.management.guard-enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(FilterRegistrationBean.class);
            assertThat(context.getBean(FilterRegistrationBean.class).getFilter())
                .as("缺 PermissionProvider 时守卫仍必须装配（空权限 = 一律拒绝），不得启动失败")
                .isInstanceOf(ManagementEndpointGuard.class);
        });
    }

    @Test
    @DisplayName("有 PermissionProvider：正常装配守卫")
    void withPermissionProvider_registersGuard() {
        runner.withPropertyValues("ypbin.security.management.guard-enabled=true")
            .withBean(PermissionProvider.class, () -> new PermissionProvider() {
            })
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(FilterRegistrationBean.class).getFilter())
                    .isInstanceOf(ManagementEndpointGuard.class);
            });
    }

    @Test
    @DisplayName("未开启 guard-enabled：整块不装配（opt-in，不打扰未配置宿主）")
    void withoutGuardEnabled_skips() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(FilterRegistrationBean.class);
        });
    }
}

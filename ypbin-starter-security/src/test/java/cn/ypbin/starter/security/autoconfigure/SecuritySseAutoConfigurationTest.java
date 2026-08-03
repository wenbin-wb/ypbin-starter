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

import cn.ypbin.starter.messaging.autoconfigure.SseAutoConfiguration;
import cn.ypbin.starter.messaging.sse.SseSubscribeController;
import cn.ypbin.starter.messaging.sse.SseTicketController;
import cn.ypbin.starter.messaging.sse.SseUserIdResolver;
import cn.ypbin.starter.security.satoken.SecurityExcludePathProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

/**
 * {@link SecuritySseAutoConfiguration} 与 messaging {@link SseAutoConfiguration} 的装配顺序回归测试。
 *
 * <p>复现 admin 实测的场景：servlet web + {@code ypbin.sse.enabled=true} + 引入 security。锁定
 * {@code @AutoConfigureBefore(SseAutoConfiguration.class)} 的效果——security 先注册 {@link SseUserIdResolver}，
 * messaging 的订阅/换票端点（{@code @ConditionalOnBean(SseUserIdResolver)}）才能被评估通过而注册。
 * 若顺序约束丢失，端点 Bean 缺失，本测试立即失败。</p>
 *
 * @author wenbin
 * @since 2026-08-03
 */
class SecuritySseAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(SecuritySseAutoConfiguration.class, SseAutoConfiguration.class))
        .withPropertyValues("ypbin.sse.enabled=true");

    @Test
    void registersResolverAndEndpoints_whenSecurityAndSsePresent() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(SseUserIdResolver.class);
            // 关键断言：端点依赖 resolver 已注册，顺序正确时才存在
            assertThat(context).hasSingleBean(SseSubscribeController.class);
            assertThat(context).hasSingleBean(SseTicketController.class);
        });
    }

    @Test
    void contributesSubscribePathToExcludes_butNotTicketPath() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(SecurityExcludePathProvider.class);
            List<String> excluded = context.getBean(SecurityExcludePathProvider.class).excludePaths();
            // 订阅端点靠 ticket 自证，须放行；换票端点靠登录态签发，不得放行
            assertThat(excluded).contains("/ypbin/sse/subscribe");
            assertThat(excluded).doesNotContain("/ypbin/sse/ticket");
        });
    }

    @Test
    void endpointsAbsent_whenSseDisabled() {
        runner.withPropertyValues("ypbin.sse.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(SseSubscribeController.class);
            assertThat(context).doesNotHaveBean(SseTicketController.class);
        });
    }
}

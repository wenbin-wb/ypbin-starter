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
package cn.ypbin.starter.web.blocklist;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.core.blocklist.BlocklistAutoConfiguration;
import cn.ypbin.starter.core.blocklist.BlocklistStore;
import cn.ypbin.starter.core.blocklist.InMemoryBlocklistStore;
import cn.ypbin.starter.web.autoconfigure.WebAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

/**
 * Web 层黑名单过滤器装配用例。
 *
 * @author wenbin
 * @since 2026-11-04
 */
class WebAutoConfigurationBlocklistTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(BlocklistAutoConfiguration.class,
            WebAutoConfiguration.class));

    @Test
    @DisplayName("enabled=true 且存在 Store 时注册过滤器")
    void registersWithStore() {
        runner.withPropertyValues("ypbin.security.blocklist.enabled=true")
            .withBean(BlocklistStore.class, InMemoryBlocklistStore::new)
            .run(context -> assertThat(context).hasBean("accessBlocklistFilterRegistration"));
    }

    @Test
    @DisplayName("缺少 Store 时不注册过滤器且启动不失败")
    void skipsWithoutStore() {
        runner.withPropertyValues("ypbin.security.blocklist.enabled=true")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean("accessBlocklistFilterRegistration");
            });
    }

    @Test
    @DisplayName("enabled=false 时即使有 Store 也不注册")
    void skipsWhenDisabled() {
        runner.withBean(BlocklistStore.class, InMemoryBlocklistStore::new)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean("accessBlocklistFilterRegistration");
            });
    }

    @Test
    @DisplayName("过滤器注册顺序来自 properties.order")
    void registrationUsesConfiguredOrder() {
        runner.withPropertyValues("ypbin.security.blocklist.enabled=true",
                "ypbin.security.blocklist.order=42")
            .withBean(BlocklistStore.class, InMemoryBlocklistStore::new)
            .run(context -> {
                FilterRegistrationBean<?> registration = context.getBean(
                    "accessBlocklistFilterRegistration", FilterRegistrationBean.class);
                assertThat(registration.getOrder()).isEqualTo(42);
            });
    }
}

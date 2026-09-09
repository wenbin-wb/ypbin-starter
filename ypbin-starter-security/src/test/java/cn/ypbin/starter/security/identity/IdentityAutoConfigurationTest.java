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
package cn.ypbin.starter.security.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

/**
 * {@link IdentityAutoConfiguration} 装配回归测试。
 *
 * <p>守护安全默认（v2.2.2 起 <code>ypbin.security.identity.enabled</code> 为显式开启）：
 * 身份头信任过滤器默认<strong>不装配</strong>，显式开启后才注册且保持最高优先级——
 * 防止后续改动把默认值改回隐式信任导致安全回归而测试无感知。</p>
 *
 * @author wenbin
 * @since 2026-09-09
 */
class IdentityAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(IdentityAutoConfiguration.class));

    @Test
    void filterAbsentByDefault() {
        // 安全默认：未显式开启时不得信任外部身份头（matchIfMissing=false）
        runner.run(context -> {
            Map<String, FilterRegistrationBean> registrations =
                context.getBeansOfType(FilterRegistrationBean.class);
            assertThat(registrations).isEmpty();
        });
    }

    @Test
    void filterRegisteredWhenExplicitlyEnabled() {
        runner.withPropertyValues("ypbin.security.identity.enabled=true")
            .run(context -> {
                FilterRegistrationBean<?> registration =
                    context.getBean("identityHeaderFilterRegistration", FilterRegistrationBean.class);
                assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
                assertThat(registration.getFilter()).isInstanceOf(IdentityHeaderFilter.class);
            });
    }

    @Test
    void filterAbsentWhenExplicitlyDisabled() {
        runner.withPropertyValues("ypbin.security.identity.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean("identityHeaderFilterRegistration"));
    }
}

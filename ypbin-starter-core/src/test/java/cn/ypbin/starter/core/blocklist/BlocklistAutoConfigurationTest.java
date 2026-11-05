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
package cn.ypbin.starter.core.blocklist;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 黑名单核心自动配置用例。
 *
 * @author wenbin
 * @since 2026-11-04
 */
class BlocklistAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(BlocklistAutoConfiguration.class,
            ClientIpResolverAutoConfiguration.class));

    @Test
    @DisplayName("默认关闭时不装配匹配器/编码器，但 IP 解析器仍可用")
    void disabledByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(BlocklistMatcher.class);
            assertThat(context).doesNotHaveBean(BlocklistRuleCodec.class);
            assertThat(context).hasSingleBean(ClientIpResolver.class);
        });
    }

    @Test
    @DisplayName("显式开启后装配三者，且 IP 解析器使用 trusted-proxies")
    void enabledRegistersBeans() {
        runner.withPropertyValues(
                "ypbin.security.blocklist.enabled=true",
                "ypbin.security.blocklist.trusted-proxies[0]=10.0.0.1")
            .run(context -> {
                assertThat(context).hasSingleBean(BlocklistMatcher.class);
                assertThat(context).hasSingleBean(BlocklistRuleCodec.class);
                assertThat(context.getBean(ClientIpResolver.class)
                    .resolve("10.0.0.1", "203.0.113.9")).isEqualTo("203.0.113.9");
            });
    }
}

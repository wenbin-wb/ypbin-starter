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
package cn.ypbin.starter.security.blocklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import cn.ypbin.starter.core.blocklist.BlocklistAutoConfiguration;
import cn.ypbin.starter.core.blocklist.BlocklistRuleCodec;
import cn.ypbin.starter.core.blocklist.BlocklistStore;
import cn.ypbin.starter.core.blocklist.InMemoryBlocklistStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 黑名单存储自动配置用例（有/无可选 Redis 两侧）。
 *
 * @author wenbin
 * @since 2026-11-04
 */
class BlocklistStoreAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(BlocklistAutoConfiguration.class,
            BlocklistStoreAutoConfiguration.class));

    @Test
    @DisplayName("关闭时仍提供内存 Store，保证登录失败计数入口可注入")
    void disabledStillProvidesStore() {
        runner.run(context -> assertThat(context.getBean(BlocklistStore.class))
            .isInstanceOf(InMemoryBlocklistStore.class));
    }

    @Test
    @DisplayName("开启但无 Redis 时使用内存 Store")
    void enabledWithoutRedisUsesMemory() {
        runner.withPropertyValues("ypbin.security.blocklist.enabled=true")
            .run(context -> assertThat(context.getBean(BlocklistStore.class))
                .isInstanceOf(InMemoryBlocklistStore.class));
    }

    @Test
    @DisplayName("开启且有 StringRedisTemplate 时使用 Redis Store")
    void enabledWithRedisUsesRedis() {
        runner.withPropertyValues("ypbin.security.blocklist.enabled=true")
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .run(context -> assertThat(context.getBean(BlocklistStore.class))
                .isInstanceOf(RedisBlocklistStore.class));
    }

    @Test
    @DisplayName("classpath 缺少 Redis 类型时自动配置不失败并退回内存")
    void missingRedisClassFallsBackToMemory() {
        runner.withPropertyValues("ypbin.security.blocklist.enabled=true")
            .withClassLoader(new FilteredClassLoader(StringRedisTemplate.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(BlocklistStore.class)).isInstanceOf(InMemoryBlocklistStore.class);
            });
    }

    @Test
    @DisplayName("宿主自定义 Store 时按类型让位")
    void customStoreWins() {
        InMemoryBlocklistStore custom = new InMemoryBlocklistStore();
        runner.withBean(BlocklistStore.class, () -> custom)
            .run(context -> assertThat(context.getBean(BlocklistStore.class)).isSameAs(custom));
    }

    @Test
    @DisplayName("缺少 BlocklistRuleCodec 时 Redis 分支不装配")
    void redisBranchNeedsCodec() {
        runner.withPropertyValues("ypbin.security.blocklist.enabled=true")
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withBean(BlocklistRuleCodec.class, BlocklistRuleCodec::new)
            .run(context -> assertThat(context).hasSingleBean(BlocklistStore.class));
    }
}

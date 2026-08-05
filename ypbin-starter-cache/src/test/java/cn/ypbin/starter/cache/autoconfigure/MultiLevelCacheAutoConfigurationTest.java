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
package cn.ypbin.starter.cache.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import cn.ypbin.starter.cache.multilevel.MultiLevelCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link MultiLevelCacheAutoConfiguration} 装配测试。
 *
 * <p>锁定「有 Caffeine 但无 spring-data-redis」场景：即便显式 {@code multi-level.enabled=true}，上下文也必须
 * 正常启动、不因配置类内省触碰 Redis 类型而抛 {@code NoClassDefFoundError}，且不装配多级缓存。防回归。</p>
 *
 * @author wenbin
 * @since 2026-08-05
 */
class MultiLevelCacheAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MultiLevelCacheAutoConfiguration.class));

    @Test
    void withoutRedis_startsAndSkipsMultiLevel_evenWhenEnabled() {
        runner.withClassLoader(new FilteredClassLoader(StringRedisTemplate.class))
            .withPropertyValues("ypbin.cache.multi-level.enabled=true")
            .run(context -> {
                // 关键：无 Redis 时整类跳过，上下文不崩
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(MultiLevelCacheService.class);
            });
    }

    @Test
    void withoutEnabled_skipsMultiLevel() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(MultiLevelCacheService.class);
        });
    }
}

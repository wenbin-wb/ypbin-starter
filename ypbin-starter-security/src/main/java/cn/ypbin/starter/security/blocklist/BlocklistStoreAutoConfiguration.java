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

import cn.ypbin.starter.core.blocklist.BlocklistProperties;
import cn.ypbin.starter.core.blocklist.BlocklistRuleCodec;
import cn.ypbin.starter.core.blocklist.BlocklistStore;
import cn.ypbin.starter.core.blocklist.InMemoryBlocklistStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 黑名单存储自动配置。
 *
 * <p>有 Redis 时用 {@link RedisBlocklistStore} 让多节点共享规则与自动封禁状态；没有 Redis 时退回
 * {@link InMemoryBlocklistStore}。与密码错误计数存储相同，用 {@code @Import} 先注册 Redis 实现，
 * 内存兜底再以 {@code @ConditionalOnMissingBean} 退让。</p>
 *
 * @author wenbin
 * @since 2026-11-04
 */
@AutoConfiguration
@AutoConfigureAfter(name = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration")
@AutoConfigureBefore(name = "cn.ypbin.starter.web.autoconfigure.WebAutoConfiguration")
@EnableConfigurationProperties(BlocklistProperties.class)
@Import(BlocklistStoreAutoConfiguration.RedisStoreConfiguration.class)
public class BlocklistStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(BlocklistStore.class)
    public BlocklistStore inMemoryBlocklistStore() {
        return new InMemoryBlocklistStore();
    }

    /**
     * 登录失败计数入口：供密码锁定与宿主自定义登录流程调用。
     */
    @Bean
    @ConditionalOnMissingBean
    public BlocklistAutoBanRecorder blocklistAutoBanRecorder(BlocklistProperties properties,
                                                              BlocklistStore store) {
        return new BlocklistAutoBanRecorder(properties, store);
    }

    /**
     * Redis 存储配置：类级条件避免无 spring-data-redis 时内省 Bean 方法参数导致启动失败。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = BlocklistProperties.PREFIX, name = "enabled", havingValue = "true")
    static class RedisStoreConfiguration {

        @Bean
        @ConditionalOnMissingBean(BlocklistStore.class)
        public BlocklistStore redisBlocklistStore(StringRedisTemplate redisTemplate,
                                                  BlocklistRuleCodec codec) {
            return new RedisBlocklistStore(redisTemplate, codec);
        }
    }
}

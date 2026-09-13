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
package cn.ypbin.starter.test.container;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Redis 集成测试支撑：按外部实例优先、容器回退的顺序提供 {@link RedisConnectionFactory}。
 *
 * <p>典型用法（配合 {@code @EnabledIfRedisAvailable} 与 {@code ApplicationContextRunner}）：</p>
 * <pre>{@code
 * @EnabledIfRedisAvailable
 * class MyRedisIT {
 *     ApplicationContextRunner runner() {
 *         return new ApplicationContextRunner()
 *             .withBean(RedisConnectionFactory.class, RedisIntegrationTestSupport::connectionFactory);
 *     }
 * }
 * }</pre>
 *
 * <p>无中间件的开发机上测试会被跳过（而非失败）；CI 上有 Docker 时自动拉起 Redis 容器。</p>
 *
 * @author wenbin
 * @since 2026-09-13
 */
public final class RedisIntegrationTestSupport {

    private RedisIntegrationTestSupport() {
    }

    /**
     * 创建指向「外部实例或容器」的 Redis 连接工厂。
     *
     * <p>口令通过 {@link RedisStandaloneConfiguration} 注入，而非已废弃的
     * {@code LettuceConnectionFactory#setPassword(String)}；空口令表示无鉴权实例。</p>
     *
     * @return 连接工厂（调用方负责生命周期，Spring 容器托管时会自动关闭）
     */
    public static RedisConnectionFactory connectionFactory() {
        RedisStandaloneConfiguration standalone =
            new RedisStandaloneConfiguration(ContainerSupport.redisHost(), ContainerSupport.redisPort());
        String password = ContainerSupport.redisPassword();
        if (password != null && !password.isBlank()) {
            standalone.setPassword(RedisPassword.of(password));
        }
        LettuceConnectionFactory factory = new LettuceConnectionFactory(standalone);
        factory.afterPropertiesSet();
        return factory;
    }

    /**
     * 用于测试隔离的键前缀（每个测试类建议使用不同前缀并在结束时清理）。
     *
     * @param suffix 业务后缀
     * @return 完整键前缀
     */
    public static String keyPrefix(String suffix) {
        return "ypbin:it:" + suffix + ":";
    }
}

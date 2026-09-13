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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MySQL 集成测试支撑：按外部实例优先、容器回退的顺序提供数据源连接信息。
 *
 * <p>返回的是 Spring Boot 标准属性名，可直接注入环境，便于用
 * {@code ApplicationContextRunner#withPropertyValues} 或 {@code @DynamicPropertySource} 装配：</p>
 * <pre>{@code
 * @EnabledIfMySqlAvailable
 * class MySqlIT {
 *     @DynamicPropertySource
 *     static void props(DynamicPropertyRegistry registry) {
 *         MySqlIntegrationTestSupport.springProperties()
 *             .forEach(registry::add);
 *     }
 * }
 * }</pre>
 *
 * @author wenbin
 * @since 2026-09-13
 */
public final class MySqlIntegrationTestSupport {

    private MySqlIntegrationTestSupport() {
    }

    /**
     * 以 Spring Boot 标准属性名返回数据源配置。
     *
     * @return 属性键值（有序）
     */
    public static Map<String, String> springProperties() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("spring.datasource.url", ContainerSupport.mySqlUrl());
        properties.put("spring.datasource.username", ContainerSupport.mySqlUsername());
        properties.put("spring.datasource.password", ContainerSupport.mySqlPassword());
        return properties;
    }

    /**
     * 执行建表脚本（按顺序）。
     *
     * <p>集成测试需要真实表结构，通常由模块自身的迁移脚本提供；本方法只是便捷入口，
     * 便于测试内联少量 DDL（如临时表）。</p>
     *
     * @param statements DDL 语句
     */
    public static void execute(String... statements) {
        try (Connection connection = DriverManager.getConnection(
                ContainerSupport.mySqlUrl(), ContainerSupport.mySqlUsername(),
                ContainerSupport.mySqlPassword());
             Statement statement = connection.createStatement()) {
            for (String ddl : statements) {
                statement.execute(ddl);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("执行集成测试 DDL 失败", e);
        }
    }
}

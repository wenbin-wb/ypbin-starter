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

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 集成测试容器/外部实例解析器。
 *
 * <p>集成测试需要真实中间件（Redis、MySQL、Nacos）。本类给出统一的三级解析策略，让同一套测试
 * 在「本地已有实例」「本地有 Docker」「两者都没有」三种环境下都能合理工作：</p>
 *
 * <ol>
 *   <li><b>外部实例优先</b>：通过环境变量显式提供连接信息时直接复用（如本机/测试服已有 Redis）；</li>
 *   <li><b>容器回退</b>：未提供外部实例且 Docker 可用时，用 Testcontainers 拉起对应镜像；</li>
 *   <li><b>跳过</b>：两者都不可用时由 {@link #available()} 返回 false，测试通过
 *       {@code @EnabledIf...} 条件跳过而不是失败，避免在无中间件的开发机上产生假失败。</li>
 * </ol>
 *
 * <p>容器实例按 JVM 复用（首次创建后缓存），避免每个测试类重复拉起、拖慢构建。</p>
 *
 * @author wenbin
 * @since 2026-09-13
 */
public final class ContainerSupport {

    private static final Logger log = LoggerFactory.getLogger(ContainerSupport.class);

    /** 外部 Redis 主机环境变量 */
    public static final String ENV_REDIS_HOST = "YPBIN_TEST_REDIS_HOST";

    /** 外部 Redis 端口环境变量 */
    public static final String ENV_REDIS_PORT = "YPBIN_TEST_REDIS_PORT";

    /** 外部 Redis 口令环境变量（设置即视为「使用外部 Redis」） */
    public static final String ENV_REDIS_PASSWORD = "YPBIN_TEST_REDIS_PASSWORD";

    /** 外部 MySQL JDBC URL 环境变量（设置即视为「使用外部 MySQL」） */
    public static final String ENV_MYSQL_URL = "YPBIN_TEST_MYSQL_URL";

    /** 外部 MySQL 用户名 */
    public static final String ENV_MYSQL_USERNAME = "YPBIN_TEST_MYSQL_USERNAME";

    /** 外部 MySQL 口令 */
    public static final String ENV_MYSQL_PASSWORD = "YPBIN_TEST_MYSQL_PASSWORD";

    /** Redis 镜像（与部署 compose 对齐主版本） */
    public static final String REDIS_IMAGE = "redis:7-alpine";

    /** MySQL 镜像（与部署 compose 对齐） */
    public static final String MYSQL_IMAGE = "mysql:8.4";

    /** Nacos 镜像（与部署 compose 对齐主版本） */
    public static final String NACOS_IMAGE = "nacos/nacos-server:v3.2.4";

    private static final Map<String, String> EXTERNAL = System.getenv();

    private static volatile GenericContainer<?> redisContainer;

    private static volatile GenericContainer<?> mysqlContainer;

    private static volatile Boolean dockerAvailable;

    private ContainerSupport() {
    }

    /**
     * Docker 是否可用。
     *
     * <p>失败后缓存结果：Docker 不可用属环境事实，反复探测只会拖慢构建并刷无意义日志。</p>
     *
     * @return 可用返回 true
     */
    public static boolean dockerAvailable() {
        Boolean cached = dockerAvailable;
        if (cached != null) {
            return cached;
        }
        synchronized (ContainerSupport.class) {
            if (dockerAvailable == null) {
                try {
                    dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
                } catch (RuntimeException e) {
                    log.debug("[ypbin-test] Docker 不可用：{}", e.getMessage());
                    dockerAvailable = false;
                }
            }
            return dockerAvailable;
        }
    }

    /** 是否配置了外部 Redis */
    public static boolean externalRedisConfigured() {
        return hasText(EXTERNAL.get(ENV_REDIS_HOST)) || hasText(EXTERNAL.get(ENV_REDIS_PASSWORD));
    }

    /** 是否配置了外部 MySQL */
    public static boolean externalMySqlConfigured() {
        return hasText(EXTERNAL.get(ENV_MYSQL_URL));
    }

    /**
     * Redis 是否可用于集成测试（外部实例或 Docker 容器任一可用）。
     *
     * @return 可用返回 true
     */
    public static boolean redisAvailable() {
        return externalRedisConfigured() || dockerAvailable();
    }

    /**
     * MySQL 是否可用于集成测试（外部实例或 Docker 容器任一可用）。
     *
     * @return 可用返回 true
     */
    public static boolean mySqlAvailable() {
        return externalMySqlConfigured() || dockerAvailable();
    }

    /**
     * 获取 Redis 主机。
     *
     * @return 主机名
     */
    public static String redisHost() {
        if (externalRedisConfigured()) {
            return EXTERNAL.getOrDefault(ENV_REDIS_HOST, "127.0.0.1");
        }
        return redisContainer().getHost();
    }

    /**
     * 获取 Redis 端口。
     *
     * @return 端口
     */
    public static int redisPort() {
        if (externalRedisConfigured()) {
            return Integer.parseInt(EXTERNAL.getOrDefault(ENV_REDIS_PORT, "6379"));
        }
        return redisContainer().getMappedPort(6379);
    }

    /**
     * 获取 Redis 口令（外部实例可能为空）。
     *
     * @return 口令或 null
     */
    public static String redisPassword() {
        return EXTERNAL.get(ENV_REDIS_PASSWORD);
    }

    /**
     * 获取 MySQL JDBC URL。
     *
     * @return JDBC URL
     */
    public static String mySqlUrl() {
        if (externalMySqlConfigured()) {
            return EXTERNAL.get(ENV_MYSQL_URL);
        }
        GenericContainer<?> container = mySqlContainer();
        return "jdbc:mysql://" + container.getHost() + ":" + container.getMappedPort(3306)
            + "/" + container.getEnvMap().getOrDefault("MYSQL_DATABASE", "ypbin_test")
            + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false"
            + "&allowPublicKeyRetrieval=true";
    }

    /**
     * 获取 MySQL 用户名。
     *
     * @return 用户名
     */
    public static String mySqlUsername() {
        return externalMySqlConfigured()
            ? EXTERNAL.getOrDefault(ENV_MYSQL_USERNAME, "root")
            : "root";
    }

    /**
     * 获取 MySQL 口令。
     *
     * @return 口令
     */
    public static String mySqlPassword() {
        return externalMySqlConfigured()
            ? EXTERNAL.getOrDefault(ENV_MYSQL_PASSWORD, "")
            : "test";
    }

    /**
     * 释放本类拉起的容器（一般无需手工调用，JVM 退出时 Testcontainers 会回收）。
     */
    public static synchronized void stopContainers() {
        if (redisContainer != null) {
            redisContainer.stop();
            redisContainer = null;
        }
        if (mysqlContainer != null) {
            mysqlContainer.stop();
            mysqlContainer = null;
        }
    }

    private static GenericContainer<?> redisContainer() {
        GenericContainer<?> container = redisContainer;
        if (container == null) {
            synchronized (ContainerSupport.class) {
                if (redisContainer == null) {
                    redisContainer = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                        .withExposedPorts(6379);
                    redisContainer.start();
                    log.info("[ypbin-test] Redis 容器已启动：{}:{}", redisContainer.getHost(),
                        redisContainer.getMappedPort(6379));
                }
                container = redisContainer;
            }
        }
        return container;
    }

    private static GenericContainer<?> mySqlContainer() {
        GenericContainer<?> container = mysqlContainer;
        if (container == null) {
            synchronized (ContainerSupport.class) {
                if (mysqlContainer == null) {
                    // 泛型自限定类型需先落到具名局部变量再赋给通配字段
                    MySQLContainer<?> started = new MySQLContainer<>(DockerImageName.parse(MYSQL_IMAGE))
                        .withDatabaseName("ypbin_test")
                        .withUsername("root")
                        .withPassword("test");
                    started.start();
                    mysqlContainer = started;
                    log.info("[ypbin-test] MySQL 容器已启动：{}:{}", started.getHost(),
                        started.getMappedPort(3306));
                }
                container = mysqlContainer;
            }
        }
        return container;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

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

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.mysql.MySQLContainer;
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

    /** 外部 Nacos 地址系统属性（既有用法：-Dypbin.it.nacos-addr=host:8848） */
    public static final String PROP_NACOS_ADDR = "ypbin.it.nacos-addr";

    /** 外部 Nacos 地址环境变量（容器/CI 场景更顺手） */
    public static final String ENV_NACOS_ADDR = "YPBIN_TEST_NACOS_ADDR";

    /**
     * Nacos 容器起始宿主端口。
     *
     * <p>Nacos 客户端固定按「服务端口 + 1000」连接 gRPC（3.x 客户端没有端口偏移配置项），
     * 而 Testcontainers 默认把 8848/9848 映射成互不相干的随机端口，客户端必然连不上
     * （表现为 {@code Client not connected, current status:STARTING}）。因此必须把两个端口绑定到
     * 一对相隔 {@link #NACOS_GRPC_PORT_OFFSET} 的连续空闲宿主端口。</p>
     */
    /** 外部 Redis 默认端口 */
    private static final String DEFAULT_REDIS_PORT = "6379";

    private static final int NACOS_BASE_PORT = 18848;

    /** 服务端口与 gRPC 端口的固定偏移 */
    private static final int NACOS_GRPC_PORT_OFFSET = 1000;

    private static final Map<String, String> EXTERNAL = System.getenv();

    @Nullable
    private static volatile GenericContainer<?> redisContainer;

    @Nullable
    private static volatile GenericContainer<?> mysqlContainer;

    @Nullable
    private static volatile GenericContainer<?> nacosContainer;

    /** 已解析出的 Nacos 地址（含容器模式），非空即表示已就绪 */
    @Nullable
    private static volatile String nacosAddress;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Nullable
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
        if (cached == null) {
            synchronized (ContainerSupport.class) {
                cached = dockerAvailable;
                if (cached == null) {
                    try {
                        cached = DockerClientFactory.instance().isDockerAvailable();
                    } catch (RuntimeException e) {
                        log.debug("[ypbin-test] Docker 不可用：{}", e.getMessage());
                        cached = false;
                    }
                    dockerAvailable = cached;
                }
            }
        }
        return cached;
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
            String configured = EXTERNAL.getOrDefault(ENV_REDIS_PORT, DEFAULT_REDIS_PORT);
            try {
                return Integer.parseInt(configured);
            } catch (NumberFormatException e) {
                // 不静默兜底：配置错就是配置错，给出可定位的错误信息
                throw new IllegalStateException(ENV_REDIS_PORT + " 不是合法端口：" + configured, e);
            }
        }
        return redisContainer().getMappedPort(6379);
    }

    /**
     * 获取 Redis 口令（外部实例可能为空）。
     *
     * @return 口令或 null
     */
    @Nullable
    public static String redisPassword() {
        return EXTERNAL.get(ENV_REDIS_PASSWORD);
    }

    /**
     * 获取 MySQL JDBC URL。
     *
     * @return JDBC URL
     */
    public static String mySqlUrl() {
        String external = EXTERNAL.get(ENV_MYSQL_URL);
        // 显式判空（而非复用 hasText）：NullAway 需要直接的 null 检查才能证明返回值非空
        if (external != null && !external.isBlank()) {
            return external;
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

    /** 是否显式配置了外部 Nacos 地址 */
    public static boolean externalNacosConfigured() {
        return hasText(externalNacosAddress());
    }

    /**
     * Nacos 是否可用于集成测试（外部地址或 Docker 容器任一可用）。
     *
     * @return 可用返回 true
     */
    public static boolean nacosAvailable() {
        return externalNacosConfigured() || dockerAvailable();
    }

    /**
     * Nacos 服务地址（{@code host:port}）：外部地址优先，其次拉起容器；都不可用时快速失败。
     *
     * <p>调用前一般先用 {@link #nacosAvailable()} 或 {@code @EnabledIfNacosAvailable} 判断，
     * 避免在无中间件的机器上失败。</p>
     *
     * @return Nacos 服务地址
     */
    public static String nacosServerAddress() {
        String external = externalNacosAddress();
        if (external != null) {
            return external;
        }
        if (!dockerAvailable()) {
            throw new IllegalStateException(
                "Nacos 不可用：未设置 -D" + PROP_NACOS_ADDR + " / " + ENV_NACOS_ADDR + " 且本机 Docker 不可用");
        }
        ensureNacosContainer();
        String address = nacosAddress;
        if (address == null) {
            throw new IllegalStateException("Nacos 容器已启动但地址未解析成功");
        }
        return address;
    }

    private static void ensureNacosContainer() {
        if (nacosAddress != null) {
            return;
        }
        synchronized (ContainerSupport.class) {
            if (nacosAddress != null) {
                return;
            }
            int basePort = findFreePortPair();
            GenericContainer<?> started = new GenericContainer<>(DockerImageName.parse(NACOS_IMAGE))
                .withEnv("MODE", "standalone")
                .withEnv("PREFER_HOST_MODE", "hostname")
                // Nacos 3 镜像的启动脚本强制要求鉴权三件套（缺任一即 exit 255）；
                // 显式 NACOS_AUTH_ENABLE=false 后开放 API 无需口令，测试不必维护凭据。
                .withEnv("NACOS_AUTH_ENABLE", "false")
                .withEnv("NACOS_AUTH_TOKEN", randomBase64Token())
                .withEnv("NACOS_AUTH_IDENTITY_KEY", "serverIdentity")
                .withEnv("NACOS_AUTH_IDENTITY_VALUE", randomHexIdentity())
                .withExposedPorts(8848, 9848)
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(
                    new PortBinding(Ports.Binding.bindPort(basePort), new ExposedPort(8848)),
                    new PortBinding(Ports.Binding.bindPort(basePort + NACOS_GRPC_PORT_OFFSET),
                        new ExposedPort(9848))))
                // 就绪探测用「监听端口」而非 HTTP 健康路径：Nacos 3 已移除 v2 的
                // /nacos/v1/console/health/readiness（会 404 直到超时）
                .waitingFor(Wait.forListeningPorts(8848, 9848).withStartupTimeout(Duration.ofMinutes(3)));
            started.start();
            nacosContainer = started;
            nacosAddress = started.getHost() + ":" + basePort;
            log.info("[ypbin-test] Nacos 容器已启动：{}（gRPC 端口 {}）", nacosAddress,
                basePort + NACOS_GRPC_PORT_OFFSET);
        }
    }

    /**
     * 取外部配置的 Nacos 地址：系统属性优先（兼容既有 {@code -Dypbin.it.nacos-addr} 用法），
     * 其次环境变量。
     *
     * @return 地址，未配置返回 {@code null}
     */
    @Nullable
    private static String externalNacosAddress() {
        String fromProperty = System.getProperty(PROP_NACOS_ADDR);
        if (hasText(fromProperty)) {
            return fromProperty.trim();
        }
        String fromEnv = EXTERNAL.get(ENV_NACOS_ADDR);
        if (fromEnv == null || fromEnv.isBlank()) {
            return null;
        }
        return fromEnv.trim();
    }

    /** 找一对相隔 {@link #NACOS_GRPC_PORT_OFFSET} 且都空闲的宿主端口 */
    private static int findFreePortPair() {
        for (int base = NACOS_BASE_PORT; base < NACOS_BASE_PORT + 200; base++) {
            if (isPortFree(base) && isPortFree(base + NACOS_GRPC_PORT_OFFSET)) {
                return base;
            }
        }
        throw new IllegalStateException("未找到可用的 Nacos 宿主端口对（起始 " + NACOS_BASE_PORT + "）");
    }

    private static boolean isPortFree(int port) {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress("127.0.0.1", port));
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    /** Nacos 要求 Base64 且解码后不少于 32 字节的鉴权 token */
    private static String randomBase64Token() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /** 鉴权身份值（等价于部署脚本的 rand_hex 32）。注意属性名刻意与常量区分，避免自引用歧义 */
    private static String randomHexIdentity() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
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
        if (nacosContainer != null) {
            nacosContainer.stop();
            nacosContainer = null;
            nacosAddress = null;
        }
    }

    private static GenericContainer<?> redisContainer() {
        GenericContainer<?> container = redisContainer;
        if (container == null) {
            synchronized (ContainerSupport.class) {
                container = redisContainer;
                if (container == null) {
                    container = new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE))
                        .withExposedPorts(6379);
                    container.start();
                    // 先打印再发布到静态字段：字段赋值必须是同步块内最后一步，
                    // 否则其他线程可能在「已可见但尚未完成发布」的窗口读到半初始化对象
                    log.info("[ypbin-test] Redis 容器已启动：{}:{}", container.getHost(),
                        container.getMappedPort(6379));
                    redisContainer = container;
                }
            }
        }
        return container;
    }

    private static GenericContainer<?> mySqlContainer() {
        GenericContainer<?> container = mysqlContainer;
        if (container == null) {
            synchronized (ContainerSupport.class) {
                container = mysqlContainer;
                if (container == null) {
                    // 泛型自限定类型需先落到具名局部变量再赋给通配字段
                    MySQLContainer started = new MySQLContainer(DockerImageName.parse(MYSQL_IMAGE))
                        .withDatabaseName("ypbin_test")
                        .withUsername("root")
                        .withPassword("test");
                    started.start();
                    // 同上：日志先行，字段赋值作为最后一步
                    log.info("[ypbin-test] MySQL 容器已启动：{}:{}", started.getHost(),
                        started.getMappedPort(3306));
                    container = started;
                    mysqlContainer = started;
                }
            }
        }
        return container;
    }

    private static boolean hasText(@Nullable String value) {
        return value != null && !value.isBlank();
    }
}
